package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.Request
import com.amazonaws.Response
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.handlers.RequestHandler2
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement
import com.amazonaws.services.identitymanagement.model.DeleteUserPolicyRequest
import com.amazonaws.services.identitymanagement.model.PutUserPolicyRequest
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.AccessControlList
import com.amazonaws.services.s3.model.Bucket
import com.amazonaws.services.s3.model.CanonicalGrantee
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.Owner
import com.amazonaws.services.s3.model.Permission
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult
import org.testng.annotations.AfterClass
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import java.nio.charset.StandardCharsets

import static com.eucalyptus.tests.awssdk.N4j.IAM_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.S3_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.TOKENS_ENDPOINT
import static com.eucalyptus.tests.awssdk.N4j.assertThat
import static com.eucalyptus.tests.awssdk.N4j.createAccount
import static com.eucalyptus.tests.awssdk.N4j.createUser
import static com.eucalyptus.tests.awssdk.N4j.deleteAccount
import static com.eucalyptus.tests.awssdk.N4j.eucaUUID
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfo
import static com.eucalyptus.tests.awssdk.N4j.getS3Client
import static com.eucalyptus.tests.awssdk.N4j.getUserCreds
import static com.eucalyptus.tests.awssdk.N4j.getUserKeys
import static com.eucalyptus.tests.awssdk.N4j.getYouAreClient
import static com.eucalyptus.tests.awssdk.N4j.initS3ClientWithNewAccount
import static com.eucalyptus.tests.awssdk.N4j.print
import static com.eucalyptus.tests.awssdk.N4j.testInfo
import static org.testng.Assert.*

/**
 * Test functionality for S3 bucket policies
 *
 * Related eucalyptus issues:
 *   https://eucalyptus.atlassian.net/browse/EUCA-651
 *   https://eucalyptus.atlassian.net/browse/EUCA-13195
 *
 * Relevant aws documentation:
 *   http://docs.aws.amazon.com/AmazonS3/latest/dev/using-iam-policies.html
 *   http://docs.aws.amazon.com/AmazonS3/latest/dev/access-control-auth-workflow-bucket-operation.html
 *   http://docs.aws.amazon.com/AmazonS3/latest/dev/access-control-auth-workflow-object-operation.html
 *   http://docs.aws.amazon.com/AmazonS3/latest/dev/access-policy-alternatives-guidelines.html
 */
class S3BucketPolicyTests {

  //
  private static final String requestorUser = 'user'

  // test specific
  private String bucketName
  private List<Runnable> cleanupTasks

  // test shared
  private AmazonS3Client s3
  private String account
  private Owner owner
  private String ownerName
  private String ownerId
  private String requestorAccount
  private String requestorAccountNumber
  private String requestorAccountAdminUserArn
  private AWSCredentials requestorCredentials
  private AmazonS3Client requestorS3
  private AmazonIdentityManagement requestorIam
  private AWSCredentials requestorUserCredentials
  private AmazonS3Client requestorUserS3
  private int policyChangeSleepSecs = 5
  private int bucketPolicyDeletionSleepSeconds = 0 // sleep necessary with aws

  @BeforeClass
  void init()  {
    print("### PRE SUITE SETUP - ${getClass().simpleName}")
    try {
      getCloudInfo( )
      account = "${getClass().simpleName.toLowerCase( )}-bucket-${eucaUUID()}"
      s3 = (AmazonS3Client)initS3ClientWithNewAccount(account, "admin")
      owner = s3.getS3AccountOwner()
      ownerName = owner.getDisplayName()
      ownerId = owner.getId()

      requestorAccount = "${getClass().simpleName.toLowerCase( )}-requestor-${eucaUUID()}"
      createAccount( requestorAccount )
      requestorCredentials = getUserCreds( requestorAccount, 'admin' )
      getCallerAccount( requestorCredentials ).with {
        requestorAccountNumber = it.account
        requestorAccountAdminUserArn = it.arn
      }
      requestorS3 = (AmazonS3Client)getS3Client( requestorCredentials, S3_ENDPOINT)
      requestorIam =
          getYouAreClient( requestorCredentials.AWSAccessKeyId, requestorCredentials.AWSSecretKey, IAM_ENDPOINT )
      createUser( requestorAccount, requestorUser )
      requestorUserCredentials = getUserKeys( requestorAccount, requestorUser  ).with{ it -> new BasicAWSCredentials( it['ak'], it['sk'] ) }
      requestorUserS3 = (AmazonS3Client)getS3Client( requestorUserCredentials, S3_ENDPOINT)
    } finally {
      if ( requestorUserS3 == null ) {
        teardown( )
      }
    }
  }

  @AfterClass
  void teardown() {
    print("### POST SUITE CLEANUP - ${getClass().simpleName}")
    if ( account ) {
      deleteAccount( account )
    }
    if ( requestorAccount ) {
      deleteAccount( requestorAccount )
    }
  }

//  /**
//   * Alternative init used for manually running tests against aws
//   **/
//  @BeforeClass
//  void initAws( ) {
//    print("### PRE SUITE SETUP - ${getClass().simpleName}")
//    getCloudInfo()
//    policyChangeSleepSecs = 15
//    bucketPolicyDeletionSleepSeconds = 15
//
//    account = "..." // friendly name here
//    s3 = new AmazonS3Client( new BasicAWSCredentials( 'AKI...', '...' ) )
//    s3.setRegion( Region.getRegion( Regions.US_WEST_1 ) )
//    owner = s3.getS3AccountOwner()
//    ownerName = owner.getDisplayName()
//    ownerId = owner.getId()
//
//    requestorAccount = "..." // friendly name here
//    requestorCredentials = new BasicAWSCredentials( 'AKI...', '...' )
//    requestorAccountNumber = '...' // 9-digit account#
//    requestorAccountAdminUserArn = 'arn:...' // arn for account
//    requestorS3 = new AmazonS3Client( requestorCredentials )
//    requestorS3.setRegion( Region.getRegion( Regions.US_WEST_1 ) )
//    requestorIam = new AmazonIdentityManagementClient( requestorCredentials )
//    requestorUserCredentials = new BasicAWSCredentials( 'AKI...', '...' )
//    requestorUserS3 = new AmazonS3Client( requestorUserCredentials )
//    requestorUserS3.setRegion( Region.getRegion( Regions.US_WEST_1 ) )
//  }

  @BeforeMethod
  void setup() throws Exception {
    print("Initializing bucket name and clean up tasks")
    bucketName = "b${eucaUUID()}".toLowerCase( )
    cleanupTasks = [ ]
  }

  @AfterMethod
  void cleanup() throws Exception {
    Collections.reverse(cleanupTasks)
    for (final Runnable cleanupTask : cleanupTasks) {
      try {
        cleanupTask.run()
      } catch (Exception e) {
        print("Unable to run clean up task: ${e}")
      }
    }
  }

  @Test
  void testBucketPolicyNotFound( ) {
    testInfo( "${getClass().simpleName}.testBucketPolicyNotFound" )
    createBucket( bucketName )
    // sdk converts the expected error code to null policy for you
    String policyText = s3.getBucketPolicy( bucketName ).policyText
    assertThat( policyText == null, 'Expected null bucket policy when none set, but was: ${policyText}' )
  }

  @Test
  void testBucketPolicyCrud( ) {
    testInfo( "${getClass().simpleName}.testBucketPolicyCrud" )
    createBucket( bucketName )
    // policy in "normal" form
    String policy = """{"Version":"2008-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::3feaf41a66bf0961/*"}]}"""
    s3.with {
      final RequestHandler2 statusCodeCheckingHandler = new RequestHandler2( ) {
        @Override
        void afterResponse( final Request<?> request, final Response<?> response ) {
          print( "${account}: Got response status code ${response.httpResponse.statusCode}" )
          assertEquals( 204, response.httpResponse.statusCode, 'Status code' )
        }
      };

      print( "Setting bucket ${bucketName} policy ${policy}" )
      s3.addRequestHandler( statusCodeCheckingHandler );
      setBucketPolicy( bucketName, policy )
      s3.removeRequestHandler( statusCodeCheckingHandler );

      print( "Getting bucket ${bucketName} policy" )
      String roundTripPolicy = getBucketPolicy( bucketName ).policyText
      print( "Got bucket policy ${roundTripPolicy}" )
      assertThat( policy == roundTripPolicy, "Policy changed on set/get" )

      print( "Deleting bucket ${bucketName} policy" )
      s3.addRequestHandler( statusCodeCheckingHandler );
      deleteBucketPolicy( bucketName )
      s3.removeRequestHandler( statusCodeCheckingHandler );

      assertThat( getBucketPolicy( bucketName ).policyText == null, 'Expected null bucket policy when none set' )
    }
  }

  @Test
  void testBucketPolicyIdempotency( ) {
    testInfo( "${getClass().simpleName}.testBucketPolicyIdempotency" )
    createBucket( bucketName )
    // policy in "normal" form
    String policy = """{"Version":"2008-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::${bucketName}/*"}]}"""
    s3.with {
      print( "Setting bucket ${bucketName} policy ${policy}" )
      setBucketPolicy( bucketName, policy )

      print( "Setting bucket ${bucketName} policy again ${policy}" )
      setBucketPolicy( bucketName, policy )

      print( "Getting bucket ${bucketName} policy" )
      String roundTripPolicy = getBucketPolicy( bucketName ).policyText
      print( "Got bucket policy ${roundTripPolicy}" )
      assertThat( policy == roundTripPolicy, "Policy changed on set/get" )

      print( "Deleting bucket ${bucketName} policy" )
      deleteBucketPolicy( bucketName )

      print( "Deleting bucket ${bucketName} policy again" )
      deleteBucketPolicy( bucketName )
    }
  }

  @Test
  void testBucketPolicyOverwrite( ) {
    testInfo( "${getClass().simpleName}.testBucketPolicyOverwrite" )
    createBucket( bucketName )
    // policies in "normal" form
    String policy1 = """{"Version":"2008-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:GetObject","Resource":"arn:aws:s3:::${bucketName}/*"}]}"""
    String policy2 = """{"Version":"2008-10-17","Statement":[{"Effect":"Allow","Principal":"*","Action":"s3:*","Resource":"arn:aws:s3:::${bucketName}/*"}]}"""
    s3.with {
      print( "Setting bucket ${bucketName} policy ${policy1}" )
      setBucketPolicy( bucketName, policy1 )

      print( "Changing bucket ${bucketName} policy ${policy2}" )
      setBucketPolicy( bucketName, policy2 )

      print( "Getting bucket ${bucketName} policy" )
      String roundTripPolicy = getBucketPolicy( bucketName ).policyText
      print( "Got bucket policy ${roundTripPolicy}" )
      assertThat( policy2 == roundTripPolicy, "Policy changed on set/get" )
    }
  }

  @Test
  void testAccessDeniedWithNoPermissions( ) {
    testInfo( "${getClass().simpleName}.testAccessDeniedWithNoPermissions" )
    createBucket( bucketName )
    createObject( bucketName, 'foo', 'content' )

    requestorS3.with {
      print( "Getting object foo from bucket ${bucketName} as ${requestorAccount}" )
      try {
        getObjectAsString( bucketName, 'foo' )
        fail( "Expected access denied for cross account access with no permissions" )
      } catch ( AmazonServiceException e ) {
        print( "Got expected object access error: ${e}" )
        assertEquals( e.errorCode, 'AccessDenied', 'Error code for access failure' )
      }
    }
  }

  @Test
  void testLargePolicy( ) {
    testInfo( "${getClass().simpleName}.testLargePolicy" )
    createBucket( bucketName )

    String largePolicy = """{"Version":"2012-10-17","Statement":[{"Sid": "${'a'*16_000}", "Effect":"Allow","Principal":{"AWS":"${requestorAccountNumber}"},"Action":"s3:GetObject","Resource":"arn:aws:s3:::${bucketName}/foo"}]}"""
    s3.with {
      print( "Setting bucket ${bucketName} large policy ${largePolicy}" )
      setBucketPolicy( bucketName, largePolicy )
    }
  }

  @Test
  void testBucketPolicyAllowsAccess( ) {
    testInfo( "${getClass().simpleName}.testBucketPolicyAllowsAccess" )
    createBucket( bucketName )
    createObject( bucketName, 'foo', 'content' )
    String policy1 = """{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":"${requestorAccountNumber}"},"Action":"s3:GetObject","Resource":"arn:aws:s3:::${bucketName}/foo"}]}"""
    s3.with {
      print( "Setting bucket ${bucketName} policy ${policy1}" )
      setBucketPolicy( bucketName, policy1 )
    }

    requestorS3.with {
      print( "Getting object foo from bucket ${bucketName} as ${requestorAccount}" )
      String content = getObjectAsString( bucketName, 'foo' )
      assertEquals( content, 'content', 'Content for foo' )
    }
  }

  @Test
  void testBucketPolicyAllowsAccessByUserArn( ) {
    testInfo( "${getClass().simpleName}.testBucketPolicyAllowsAccessByUserArn" )
    createBucket( bucketName )
    createObject( bucketName, 'foo', 'content' )
    String policy = """{"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":"${requestorAccountAdminUserArn}"},"Action":"s3:GetObject","Resource":"arn:aws:s3:::${bucketName}/foo"}]}"""
    s3.with {
      print( "Setting bucket ${bucketName} policy ${policy}" )
      setBucketPolicy( bucketName, policy )
    }

    requestorS3.with {
      print( "Getting object foo from bucket ${bucketName} as ${requestorAccount}" )
      String content = getObjectAsString( bucketName, 'foo' )
      assertEquals( content, 'content', 'Content for foo' )
    }
  }

  @Test
  void testBucketPolicyDeniesAccess( ) {
    testInfo( "${getClass().simpleName}.testBucketPolicyDeniesAccess" )
    createBucket( bucketName )
    createObject( bucketName, 'foo', 'content' )
    // policy in "normal" form
    String policy = """{"Version":"2012-10-17","Statement":[{"Effect":"Deny","Principal":{"AWS":"*"},"Action":"s3:GetObject","Resource":"arn:aws:s3:::${bucketName}/foo"}]}"""
    s3.with {
      print( "Setting bucket ${bucketName} policy ${policy}" )
      setBucketPolicy( bucketName, policy )

      print( "Getting object foo from bucket ${bucketName}" )
      try {
        getObjectAsString( bucketName, 'foo' )
        fail( "Expected access denied by bucket policy" )
      } catch ( AmazonServiceException e ) {
        print( "Got expected object access error: ${e}" )
        assertEquals( e.errorCode, 'AccessDenied', 'Error code for access failure' )
      }
    }
  }

  @Test
  void testArnCaseSensitivity( ) {
    testInfo( "${getClass().simpleName}.testArnCaseSensitivity" )
    createBucket( bucketName )
    createObject( bucketName, 'foo', 'content' )
    createObject( bucketName, 'FOO', 'content' )
    String policy = """\
    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Principal": {
                    "AWS": "${requestorAccountNumber}"
                },
                "Action": "s3:GetObject",
                "Resource": [
                    "arn:aws:s3:::${bucketName.toUpperCase( )}/foo",
                    "arn:aws:s3:::${bucketName}/FOO"
                ]
            }
        ]
    }
    """.stripIndent()
    s3.with {
      print( "Setting bucket ${bucketName} policy ${policy}" )
      setBucketPolicy( bucketName, policy )
    }

    requestorS3.with {
      print( "Getting object FOO from bucket ${bucketName} as ${requestorAccount}" )
      String content1 = getObjectAsString( bucketName, 'FOO' )
      assertEquals( content1, 'content', 'Content for FOO' )

      print( "Getting object foo from bucket ${bucketName}" )
      try {
        getObjectAsString( bucketName, 'foo' )
        fail( "Expected access denied by bucket policy" )
      } catch ( AmazonServiceException e ) {
        print( "Got expected object access error: ${e}" )
        assertEquals( e.errorCode, 'AccessDenied', 'Error code for access failure' )
      }
    }
  }

  @Test
  void testAnonymousAccess( ) {
    testInfo( "${getClass().simpleName}.testAnonymousAccess" )
    createBucket( bucketName )
    createObject( bucketName, 'foo', 'content' )

    String objectUrl = "${S3_ENDPOINT}${bucketName}/foo"
    print( "Accessing object ${objectUrl} without permission" )
    try {
      new URL( objectUrl ).
          getText( connectTimeout: 10000, readTimeout: 10000, useCaches: false, allowUserInteraction: false )
      fail( 'Expected object access to fail' )
    } catch ( e ) {
      print( "Expected object access error: ${e}" )
    }

    String policy = """\
    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Principal": "*",
                "Action": "s3:GetObject",
                "Resource": [
                    "arn:aws:s3:::${bucketName}/foo"
                ]
            }
        ]
    }
    """.stripIndent( )
    s3.with {
      print( "Setting bucket ${bucketName} policy ${policy}" )
      setBucketPolicy( bucketName, policy )
    }

    print( "Accessing object ${objectUrl}" )
    String content = new URL( objectUrl ).
        getText( connectTimeout: 10000, readTimeout: 10000, useCaches: false, allowUserInteraction: false )
    assertEquals( content, 'content', 'Content for foo from url' )
  }

  /**
   * Test that an identity iam policy in the requestor account can grant access when the
   * object is in the requestor account
   */
  @Test
  void testObjectAccountIamAccess( ) {
    testInfo( "${getClass().simpleName}.testObjectAccountIamAccess" )
    createBucket( bucketName )

    String policy = """\
    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Principal": {
                    "AWS": [ "${requestorAccountAdminUserArn}" ]
                },
                "Action": "s3:*",
                "Resource": [
                    "arn:aws:s3:::${bucketName}",
                    "arn:aws:s3:::${bucketName}/*"
                ]
            }
        ]
    }
    """.stripIndent( )
    s3.with {
      print( "Setting bucket ${bucketName} policy ${policy}" )
      setBucketPolicy( bucketName, policy )
    }

    createObject( requestorS3, bucketName, 'foo', 'content' )
    requestorS3.with {
      print( "Getting object foo from bucket ${bucketName} as admin in ${requestorAccount}" )
      String content = getObjectAsString( bucketName, 'foo' )
      assertEquals( content, 'content', 'Content for foo' )
    }
    s3.with {
      print( "Removing bucket ${bucketName} policy" )
      deleteBucketPolicy( bucketName )
    }
    requestorS3.with {
      print( "Getting object foo from bucket ${bucketName} as admin in ${requestorAccount} with no bucket policy" )
      String content = getObjectAsString( bucketName, 'foo' )
      assertEquals( content, 'content', 'Content for foo' )
    }

    String userPolicy = """\
    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Action": "s3:GetObject",
                "Resource": [
                    "arn:aws:s3:::${bucketName}/foo"
                ]
            }
        ]
    }
    """.stripIndent( )
    requestorUserS3.with {
      print( "Getting object foo from bucket ${bucketName} as user in ${requestorAccount} without permission" )
      try {
        getObjectAsString( bucketName, 'foo' )
        fail( "Expected access denied due to no user policy" )
      } catch ( AmazonServiceException e ) {
        print( "Got expected object access error: ${e}" )
        assertEquals( e.errorCode, 'AccessDenied', 'Error code for access failure' )
      }
    }
    print( "Putting ${requestorUser} policy allowing foo object access for bucket ${userPolicy}" )
    requestorIam.putUserPolicy( new PutUserPolicyRequest(
        userName: requestorUser,
        policyName: 's3-get-object-foo',
        policyDocument: userPolicy
    ) )
    cleanupTasks.add{
      requestorIam.deleteUserPolicy( new DeleteUserPolicyRequest(
          userName: requestorUser,
          policyName: 's3-get-object-foo'
      ) )
    }

    print( "Sleeping to allow user policy to apply" )
    N4j.sleep( policyChangeSleepSecs )
    requestorUserS3.with {
      print( "Getting object foo from bucket ${bucketName} as user in ${requestorAccount}" )
      String content = getObjectAsString( bucketName, 'foo' )
      assertEquals( content, 'content', 'Content for foo' )
    }
  }

  /**
   * Test that the object resource ownership does not matter when the object is
   * in another account an the object acl gives bucket owner permissions.
   */
  @Test
  void testObjectAccountIamAccessDeniedNoAcl( ) {
    testInfo( "${getClass().simpleName}.testObjectAccountIamAccessDeniedNoAcl" )
    createBucket( bucketName )

    String policy = """\
    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Principal": {
                    "AWS": [ "${requestorAccountAdminUserArn}" ]
                },
                "Action": "s3:*",
                "Resource": [
                    "arn:aws:s3:::${bucketName}",
                    "arn:aws:s3:::${bucketName}/*"
                ]
            }
        ]
    }
    """.stripIndent( )
    s3.with {
      print( "Setting bucket ${bucketName} policy ${policy}" )
      setBucketPolicy( bucketName, policy )
    }

    AccessControlList acl = new AccessControlList()
    acl.grantPermission(new CanonicalGrantee(ownerId), Permission.FullControl);
    createObject( requestorS3, bucketName, 'foo', 'content', acl )
    requestorS3.with {
      print( "Getting object foo from bucket ${bucketName} as admin in ${requestorAccount} with no acl permission" )
      String content = getObjectAsString( bucketName, 'foo' )
      assertEquals( content, 'content', 'Content for foo' )
    }
    s3.with {
      print( "Removing bucket ${bucketName} policy" )
      deleteBucketPolicy( bucketName )
    }

    String userPolicy = """\
    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Action": "s3:GetObject",
                "Resource": [
                    "arn:aws:s3:::${bucketName}/foo"
                ]
            }
        ]
    }
    """.stripIndent( )
    print( "Putting ${requestorUser} policy allowing foo object access for bucket ${userPolicy}" )
    requestorIam.putUserPolicy( new PutUserPolicyRequest(
        userName: requestorUser,
        policyName: 's3-get-object-foo',
        policyDocument: userPolicy
    ) )
    cleanupTasks.add{
      requestorIam.deleteUserPolicy( new DeleteUserPolicyRequest(
          userName: requestorUser,
          policyName: 's3-get-object-foo'
      ) )
    }

    print( "Sleeping to allow user policy to apply" )
    N4j.sleep( policyChangeSleepSecs )
    requestorUserS3.with {
      print( "Getting object foo from bucket ${bucketName} as user in ${requestorAccount} with no acl permission" )
      String content = getObjectAsString( bucketName, 'foo' )
      assertEquals( content, 'content', 'Content for foo' )
    }
  }

  /**
   * Test that an identity iam policy in the requestor account cannot grant access when the
   * object is in another account
   */
  @Test
  void testObjectAccountIamAccessDenied( ) {
    testInfo( "${getClass().simpleName}.testObjectAccountIamAccess" )
    createBucket( bucketName )

    String policy = """\
    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Principal": {
                    "AWS": [ "${requestorAccountAdminUserArn}" ]
                },
                "Action": "s3:*",
                "Resource": [
                    "arn:aws:s3:::${bucketName}",
                    "arn:aws:s3:::${bucketName}/*"
                ]
            }
        ]
    }
    """.stripIndent( )
    s3.with {
      print( "Setting bucket ${bucketName} policy ${policy}" )
      setBucketPolicy( bucketName, policy )
    }

    createObject( s3, bucketName, 'foo', 'content' ) // create object in bucket account
    requestorS3.with {
      print( "Getting object foo from bucket ${bucketName} as admin in ${requestorAccount}" )
      String content = getObjectAsString( bucketName, 'foo' )
      assertEquals( content, 'content', 'Content for foo' )
    }

    String userPolicy = """\
    {
        "Version": "2012-10-17",
        "Statement": [
            {
                "Effect": "Allow",
                "Action": "s3:GetObject",
                "Resource": [
                    "arn:aws:s3:::${bucketName}/foo"
                ]
            }
        ]
    }
    """.stripIndent( )
    print( "Putting ${requestorUser} policy allowing foo object access for bucket ${userPolicy}" )
    requestorIam.putUserPolicy( new PutUserPolicyRequest(
        userName: requestorUser,
        policyName: 's3-get-object-foo',
        policyDocument: userPolicy
    ) )
    cleanupTasks.add{
      requestorIam.deleteUserPolicy( new DeleteUserPolicyRequest(
          userName: requestorUser,
          policyName: 's3-get-object-foo'
      ) )
    }

    requestorUserS3.with {
      print( "Getting object foo from bucket ${bucketName} as user in ${requestorAccount} without permission" )
      try {
        getObjectAsString( bucketName, 'foo' )
        fail( "Expected access denied due to user policy not sufficient to grant access" )
      } catch ( AmazonServiceException e ) {
        print( "Got expected object access error: ${e}" )
        assertEquals( e.errorCode, 'AccessDenied', 'Error code for access failure' )
      }
    }

    s3.with {
      print( "Removing bucket ${bucketName} policy" )
      deleteBucketPolicy( bucketName )
    }
    print( "Sleeping to allow bucket policy deletion to apply" )
    N4j.sleep( bucketPolicyDeletionSleepSeconds )
    requestorS3.with {
      print( "Getting object foo from bucket ${bucketName} as admin in ${requestorAccount} without permission" )
      try {
        getObjectAsString( bucketName, 'foo' )
        fail( "Expected access denied due to no bucket policy" )
      } catch ( AmazonServiceException e ) {
        print( "Got expected object access error: ${e}" )
        assertEquals( e.errorCode, 'AccessDenied', 'Error code for access failure' )
      }
    }
  }

  private Bucket createBucket( final String bucketName ) {
    print( "${account}: Creating bucket ${bucketName}" )
    Bucket bucket = S3Utils.createBucket(s3, account, bucketName, S3Utils.BUCKET_CREATION_RETRIES)
    cleanupTasks.add {
      print( "${account}: Deleting bucket ${bucketName}" )
      s3.deleteBucket(bucketName)
    }
    bucket
  }

  private void createObject( final String bucketName, final String key, final String content ) {
    createObject( s3, bucketName, key, content )
  }

  private void createObject( final AmazonS3 s3, final String bucketName, final String key, final String content, final AccessControlList acl = null ) {
    print( "Creating object ${key} in bucket ${bucketName}" )
    s3.putObject( new PutObjectRequest(
        bucketName,
        key,
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
        new ObjectMetadata( )
    ).withAccessControlList( acl ) )
    cleanupTasks.add {
      print( "${account}: Deleting object ${key} from bucket ${bucketName}" )
      s3.deleteObject( bucketName, key )
    }
  }

  private GetCallerIdentityResult getCallerAccount(final AWSCredentials creds ) {
    new AWSSecurityTokenServiceClient( creds ).with {
      endpoint = TOKENS_ENDPOINT
      getCallerIdentity( new GetCallerIdentityRequest( ) )
    }
  }
}
