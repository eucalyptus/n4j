package com.eucalyptus.tests.awssdk

import com.amazonaws.AmazonServiceException
import com.amazonaws.auth.AWSCredentialsProvider
import com.amazonaws.auth.BasicSessionCredentials
import com.amazonaws.internal.StaticCredentialsProvider
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest
import com.amazonaws.services.identitymanagement.model.NoSuchEntityException
import com.amazonaws.services.securitytoken.AWSSecurityTokenService
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest
import com.amazonaws.services.sqs.AmazonSQS
import com.amazonaws.services.sqs.AmazonSQSClient
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest
import com.github.sjones4.youcan.youare.YouAre
import com.github.sjones4.youcan.youare.YouAreClient
import com.github.sjones4.youcan.youare.model.CreateAccountRequest
import org.testng.annotations.BeforeClass
import org.testng.annotations.Test

import static com.eucalyptus.tests.awssdk.N4j.*

/**
 * Test authorizing access via a queue policy.
 *
 * This test covers the issues:
 *   https://eucalyptus.atlassian.net/browse/EUCA-12869
 *   https://eucalyptus.atlassian.net/browse/EUCA-12897
 */
class TestSQSQueuePolicy {

  @BeforeClass
  void init() throws Exception {
    print("### PRE SUITE SETUP - ${this.getClass().simpleName}")
    getCloudInfoAndSqs( )
  }

  @Test
  void testSqsQueuePolicy( ) {
    testInfo( "${this.getClass().simpleName} - testSqsQueuePolicy")

    final String namePrefix = N4j.NAME_PREFIX

    final List<Runnable> cleanupTasks = new ArrayList<>();
    try {
      final String account = "${namePrefix}sqs-queue-policy-test"
      final String user = 'user-1'
      final String accountNumber =
          youAre.createAccount( new CreateAccountRequest( accountName: account ) ).with { it.account.accountId };
      cleanupTasks.add{
        deleteAccount( account )
      }
      createUser( account, user )
      final AmazonSQS sqs = getSqsClientWithNewAccount( account, 'admin' )
      final AmazonSQS userSqs = getSqsClientWithNewAccount( account, user )

      final String queueName = "q${namePrefix}policy-test-queue"
      final String queueUrl = sqs.with{
        print "Creating queue ${queueName}"
        final String queueUrl = createQueue( queueName ).with {
          queueUrl
        }
        print "Created queue with url ${queueUrl}"
        assertThat( queueUrl != null, 'Expected queue url' )
        cleanupTasks.add{
          print "Deleting queue ${queueName}"
          deleteQueue( queueUrl )
        }

        print "Getting queue url with admin creds"
        getQueueUrl( queueName ).with {
          print "Got queue url ${it.queueUrl}"
          assertThat( it.queueUrl != null, 'Expected queue url' )
        }

        queueUrl
      }

      userSqs.with {
        print "Getting queue url with user creds"
        try {
          getQueueUrl( queueName ).with {
            print "Got queue url ${it.queueUrl}"
            assertThat( false, 'Expected queue url access forbidden' )
          }
        } catch ( AmazonServiceException e ) {
          print "Got expected exception for queue url access ${e}"
          assertThat(
              'QueueDoesNotExist' == e.errorCode,
              "Expected error code QueueDoesNotExist, but was ${e.errorCode}")
        }

        print 'Adding IAM policy to authorize user access'
        createIAMPolicy( account, user, 'queue-url-policy',
            '{ "Statement": { "Effect": "Allow", "Action": "sqs:GetQueueUrl", "Resource": "*" } }' );

        print 'Sleeping to allow policy to be in use'
        N4j.sleep 5

        print "Getting queue url with user creds"
        getQueueUrl( queueName ).with {
          print "Got queue url ${it.queueUrl}"
          assertThat( it.queueUrl != null, 'Expected queue url' )
        }

        print 'Updating IAM policy to deauthorize user access'
        createIAMPolicy( account, user, 'queue-url-policy',
            '{ "Statement": { "Effect": "Allow", "Action": "sqs:GetQueueAttributes", "Resource": "*" } }' );

        print 'Sleeping to allow policy to be in use'
        N4j.sleep 5

        print "Getting queue url with user creds"
        try {
          getQueueUrl( queueName ).with {
            print "Got queue url ${it.queueUrl}"
            assertThat( false, 'Expected queue url access forbidden' )
          }
        } catch ( AmazonServiceException e ) {
          print "Got expected exception for queue url access ${e}"
          assertThat(
              'QueueDoesNotExist' == e.errorCode,
              "Expected error code QueueDoesNotExist, but was ${e.errorCode}")
        }

        void
      }

      sqs.with {
        print "Setting policy for queue to authorize account"
        setQueueAttributes( new SetQueueAttributesRequest(
            queueUrl: queueUrl,
            attributes: [
                Policy: """\
                {
                    "Statement": {
                      "Effect": "Allow",
                      "Principal": {
                         "AWS": "${accountNumber}"
                      },
                      "Action": "sqs:GetQueueUrl"
                    }
                }
                """.stripIndent( )
            ]
        ) )
      }

      // test access fails when only account is a authorized by resource policy
      userSqs.with {
        print "Getting queue url with user creds"
        try {
          getQueueUrl( queueName ).with {
            print "Got queue url ${it.queueUrl}"
            assertThat( false, 'Expected queue url access forbidden' )
          }
        } catch ( AmazonServiceException e ) {
          print "Got expected exception for queue url access ${e}"
          assertThat(
              'QueueDoesNotExist' == e.errorCode,
              "Expected error code QueueDoesNotExist, but was ${e.errorCode}")
        }

        void
      }

      sqs.with {
        print "Setting policy for queue to authorize user"
        setQueueAttributes( new SetQueueAttributesRequest(
            queueUrl: queueUrl,
            attributes: [
                Policy: """\
                {
                    "Statement": {
                      "Effect": "Allow",
                      "Principal": {
                         "AWS": "arn:aws:iam::${accountNumber}:user/${user}"
                      },
                      "Action": "sqs:GetQueueUrl"
                    }
                }
                """.stripIndent( )
            ]
        ) )
      }

      // test access succeeds when user is a authorized by resource policy
      userSqs.with {
        print "Getting queue url with user creds"
        getQueueUrl( queueName ).with {
          print "Got queue url ${it.queueUrl}"
          assertThat( it.queueUrl != null, 'Expected queue url' )
        }

        void
      }

      final AWSCredentialsProvider awsCredentialsProvider =
          new StaticCredentialsProvider( getUserCreds( account,'admin' ) );
      final YouAre accountYouAre = new YouAreClient( awsCredentialsProvider );
      accountYouAre.setEndpoint( IAM_ENDPOINT );
      final String role = "${namePrefix}role-1"
      print "Creating role for test use ${role}"
      final String roleArn = accountYouAre.createRole( new CreateRoleRequest(
          roleName: role,
          path: '/',
          assumeRolePolicyDocument: """\
          {
              "Statement": {
                "Effect": "Allow",
                "Principal": {
                   "AWS": "${accountNumber}"
                },
                "Action": "sts:AssumeRole"
              }
          }
          """.stripIndent( )
      ) ).with {
        it.role.arn
      }
      print "Created role ${roleArn}"
      assertThat( roleArn != null, 'Expected role arn' )

      final AWSSecurityTokenService sts = new AWSSecurityTokenServiceClient( awsCredentialsProvider );
      sts.setEndpoint( TOKENS_ENDPOINT );
      final AWSCredentialsProvider roleCredentialsProvider = sts.with {
        assumeRole( new AssumeRoleRequest(
            roleArn: roleArn,
            roleSessionName: 'queue-policy-test-session'
        ) ).with {
          new StaticCredentialsProvider( new BasicSessionCredentials(
              credentials.accessKeyId,
              credentials.secretAccessKey,
              credentials.sessionToken
          ) )
        }
      }
      final AmazonSQS roleSqs = new AmazonSQSClient( roleCredentialsProvider );
      roleSqs.setEndpoint( SQS_ENDPOINT );

      // test access fails when role not permitted
      roleSqs.with {
        print "Getting queue url with role creds"
        try {
          getQueueUrl( queueName ).with {
            print "Got queue url ${it.queueUrl}"
            assertThat( false, 'Expected queue url access forbidden' )
          }
        } catch ( AmazonServiceException e ) {
          print "Got expected exception for queue url access ${e}"
          assertThat(
              'QueueDoesNotExist' == e.errorCode,
              "Expected error code QueueDoesNotExist, but was ${e.errorCode}")
        }

        void
      }

      sqs.with {
        print "Setting policy for queue to authorize role"
        setQueueAttributes( new SetQueueAttributesRequest(
            queueUrl: queueUrl,
            attributes: [
                Policy: """\
                {
                    "Statement": {
                      "Effect": "Allow",
                      "Principal": {
                         "AWS": "arn:aws:iam::${accountNumber}:role/${role}"
                      },
                      "Action": "sqs:GetQueueUrl"
                    }
                }
                """.stripIndent( )
            ]
        ) )
      }

      // test access succeeds when role is a authorized by resource policy
      roleSqs.with {
        print "Getting queue url with role creds"
        getQueueUrl( queueName ).with {
          print "Got queue url ${it.queueUrl}"
          assertThat( it.queueUrl != null, 'Expected queue url' )
        }

        void
      }

      sqs.with {
        print "Setting policy for queue to authorize assumed role"
        setQueueAttributes( new SetQueueAttributesRequest(
            queueUrl: queueUrl,
            attributes: [
                Policy: """\
                {
                    "Statement": {
                      "Effect": "Allow",
                      "Principal": {
                         "AWS": "arn:aws:sts::${accountNumber}:assumed-role/${role}/queue-policy-test-session"
                      },
                      "Action": "sqs:GetQueueUrl"
                    }
                }
                """.stripIndent( )
            ]
        ) )
      }

      // test access succeeds when assumed role is a authorized by resource policy
      roleSqs.with {
        print "Getting queue url with role creds"
        getQueueUrl( queueName ).with {
          print "Got queue url ${it.queueUrl}"
          assertThat( it.queueUrl != null, 'Expected queue url' )
        }

        void
      }

      print 'Test complete'
    } finally {
      // Attempt to clean up anything we created
      Collections.reverse(cleanupTasks);
      for (final Runnable cleanupTask : cleanupTasks) {
        try {
          cleanupTask.run();
        } catch (NoSuchEntityException e) {
          print("Entity not found during cleanup.");
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }
}
