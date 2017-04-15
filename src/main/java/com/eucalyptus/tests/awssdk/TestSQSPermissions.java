package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.eucalyptus.tests.awssdk.N4j.LOCAL_EUCTL_FILE;
import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getConfigProperty;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSPermissions {

  private int MAX_LABEL_LENGTH_CHARS;

  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      MAX_LABEL_LENGTH_CHARS = getLocalConfigInt("MAX_LABEL_LENGTH_CHARS");
      account = "sqs-account-a-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      otherAccount = "sqs-account-b-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(otherAccount);
      otherAccountSQSClient = getSqsClientWithNewAccount(otherAccount, "admin");
    } catch (Exception e) {
      try {
        teardown();
      } catch (Exception ie) {
      }
      throw e;
    }
  }

  @AfterClass
  public void teardown() throws Exception {
    print("### POST SUITE CLEANUP - " + this.getClass().getSimpleName());
    if (account != null) {
      if (accountSQSClient != null) {
        ListQueuesResult listQueuesResult = accountSQSClient.listQueues();
        if (listQueuesResult != null) {
          listQueuesResult.getQueueUrls().forEach(accountSQSClient::deleteQueue);
        }
      }
      SQSUtils.synchronizedDeleteAccount(account);
    }
    if (otherAccount != null) {
      if (otherAccountSQSClient != null) {
        ListQueuesResult listQueuesResult = otherAccountSQSClient.listQueues();
        if (listQueuesResult != null) {
          listQueuesResult.getQueueUrls().forEach(otherAccountSQSClient::deleteQueue);
        }
      }
      SQSUtils.synchronizedDeleteAccount(otherAccount);
    }
  }

  @Test
  public void testAddPermissionNonExistentAccountQueueUrl() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermissionNonExistentAccount");
    String queueName = "queue_name_add_permission_nonexistent_account_url";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    try {
      accountSQSClient.addPermission(queueUrl.replace(accountId, "000000000000"), "label", Lists.newArrayList(accountId), Lists.newArrayList("SendMessage"));
      assertThat(false, "Should fail adding permission on a queue url from a non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail adding permission on a queue url from a non-existent user");
    }
  }

  @Test
  public void testAddPermissionOtherAccountQueueUrl() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermissionOtherAccount");
    String queueName = "queue_name_add_permission_other_account_url";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String otherAccountQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.addPermission(otherAccountQueueUrl, "label", Lists.newArrayList(accountId), Lists.newArrayList("SendMessage"));
      assertThat(false, "Should fail adding permission on a queue url from a different user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail adding permission on a queue url from a different user");
    }
  }

  @Test
  public void testAddPermissionNonExistentQueueUrl() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermissionOtherAccount");
    String queueName = "queue_name_add_permission_nonexistent_queue_url";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    try {
      accountSQSClient.addPermission(queueUrl + "-bogus", "label", Lists.newArrayList(accountId), Lists.newArrayList("SendMessage"));
      assertThat(false, "Should fail adding permission on a nonexistent queue url");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail adding permission on a nonexistent queue url");
    }
  }

  @Test
  public void testAddPermissionBadLabel() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermissionBadLabel");
    String queueName = "queue_name_add_permission_bad_label";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    // do a good test first, so we have a label that exists
    accountSQSClient.addPermission(queueUrl, "label", Lists.newArrayList(accountId), Lists.newArrayList("SendMessage"));
    // Specifically test: null, empty, too long, invalid characters, and existing label
    // use optional as guava doesn't like null map keys
    Map<Optional<String>, String> badLabelsAndReason = ImmutableMap.of(
      Optional.absent(), "is null",
      Optional.of(""), "is empty",
      Optional.of(Strings.repeat("X", 1 + MAX_LABEL_LENGTH_CHARS)), "is too long",
      Optional.of("@#$@#$@#$!"), "contains invalid characters",
      Optional.of("label"), "already exists");
    for (Optional<String> badLabelOptional: badLabelsAndReason.keySet()) {
      String badLabel = badLabelOptional.isPresent() ? badLabelOptional.get() : null;
      try {
        accountSQSClient.addPermission(queueUrl, badLabel, Lists.newArrayList(accountId), Lists.newArrayList("SendMessage"));
        assertThat(false, "Should fail adding permission on a queue with a label that " + badLabelsAndReason.get(badLabel));
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail adding permission on a queue with a label that " + badLabelsAndReason.get(badLabel));
      }
    }
  }

  @Test
  public void testAddPermissionActions() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermissionActions");
    String queueName = "queue_name_add_permission_actions";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    Set<Optional<List<String>>> badActionListsOptional = ImmutableSet.of(
      // first all legal actions that just aren't allowed
      Optional.of(ImmutableList.of("AddPermission")),
      Optional.of(ImmutableList.of("CreateQueue")),
      Optional.of(ImmutableList.of("DeleteQueue")),
      Optional.of(ImmutableList.of("ListQueues")),
      Optional.of(ImmutableList.of("SetQueueAttributes")),
      Optional.of(ImmutableList.of("RemovePermission")),
      // Now something to show it just takes one bad action
      Optional.of(ImmutableList.of("*", "SendMessage", "ReceiveMessage", "DeleteMessage", "ChangeMessageVisibility", "GetQueueAttributes",
        "GetQueueUrl", "ListDeadLetterSourceQueues", "DeleteQueue", "PurgeQueue")),
      // Now just nonexistent actions
      Optional.of(ImmutableList.of("RunInstance")),
      Optional.of(Collections.emptyList()),
      Optional.absent()
    );
    for (Optional<List<String>> badActionListOptional: badActionListsOptional) {
      List<String> badActionList = badActionListOptional.isPresent() ? badActionListOptional.get() : null;
      try {
        accountSQSClient.addPermission(queueUrl, "badActionLabel", Lists.newArrayList(accountId), badActionList);
        assertThat(false, "Should fail adding permission on a queue with bad actions");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail adding permission on a queue with bad actions");
      }
    }
    // Now test all 'good' values (at least as far as having the call come back successfully.  We will look at generated policies later.
    accountSQSClient.addPermission(queueUrl, "goodActionLabel", Lists.newArrayList(accountId), ImmutableList.of(
      "*", "SendMessage", "ReceiveMessage", "DeleteMessage", "ChangeMessageVisibility", "GetQueueAttributes",
      "GetQueueUrl", "ListDeadLetterSourceQueues", "PurgeQueue"
    ));
  }

  @Test
  public void testAddPermissionAccounts() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermissionAccounts");
    String queueName = "queue_name_add_permission_accounts";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String otherQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> otherPathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(otherQueueUrl).getPath()));
    String otherAccountId = otherPathParts.get(0);
    // Strangely. even one good account is ok (it just ignores the others -- we will see an example in another test)
    Set<List<String>> goodAccountLists = ImmutableSet.of(
      ImmutableList.of(accountId),
      ImmutableList.of(otherAccountId),
      ImmutableList.of(accountId, otherAccountId),
      ImmutableList.of(accountId, "000000000000"),
      ImmutableList.of(otherAccountId, "000000000000", "BS2123AAAZZZ")
    );
    int loopNum = 0;
    for (List<String> goodAccountList: goodAccountLists) {
      accountSQSClient.addPermission(queueUrl, "goodAccountLabel" + loopNum++, goodAccountList, Lists.newArrayList("SendMessage"));
    }

    // Now, bad accounts
    Set<Optional<List<String>>> badAccountListsOptional = ImmutableSet.of(
      Optional.of(ImmutableList.of("000000000000")),
      Optional.of(ImmutableList.of("000000000000", "BS2123AAAZZZ")),
      Optional.of(Collections.emptyList()),
      Optional.absent()
    );

    for (Optional<List<String>> badAccountListOptional: badAccountListsOptional) {
      List<String> badAccountList = badAccountListOptional.isPresent() ? badAccountListOptional.get() : null;
      try {
        accountSQSClient.addPermission(queueUrl, "badAccountLabel", Lists.newArrayList(accountId), badAccountList);
        assertThat(false, "Should fail adding permission on a queue with bad actions");
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail adding permission on a queue with bad accounts");
      }
    }
  }

  @Test
  public void testAddPermissionsFromEmptyPolicy() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermissionsFromEmptyPolicy");
    String queueName = "queue_name_add_permissions_from_empty_policy";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String otherQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> otherPathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(otherQueueUrl).getPath()));
    String otherAccountId = otherPathParts.get(0);

    accountSQSClient.addPermission(queueUrl, "label1", Lists.newArrayList(accountId), ImmutableList.of(
      "*", "SendMessage", "ReceiveMessage", "DeleteMessage", "ChangeMessageVisibility", "GetQueueAttributes",
      "GetQueueUrl", "ListDeadLetterSourceQueues", "PurgeQueue"
    ));

    accountSQSClient.addPermission(queueUrl, "label2", Lists.newArrayList(accountId, otherAccountId), ImmutableList.of(
      "*", "SendMessage", "ReceiveMessage"));

    accountSQSClient.addPermission(queueUrl, "label3", Lists.newArrayList("000000000000", otherAccountId), ImmutableList.of(
      "DeleteMessage"));

    String policyTemplate = new String("{" +
      "" +
      "    'Version':'2008-10-17'," +
      "    'Id':'%QUEUE_ARN%/SQSDefaultPolicy'," +
      "    'Statement':[" +
      "        {" +
      "            'Sid':'label1'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%ACCOUNT_ID%:root'" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'," +
      "                'SQS:DeleteMessage'," +
      "                'SQS:ChangeMessageVisibility'," +
      "                'SQS:GetQueueAttributes'," +
      "                'SQS:GetQueueUrl'," +
      "                'SQS:ListDeadLetterSourceQueues'," +
      "                'SQS:PurgeQueue'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::%ACCOUNT_ID%:root'," +
      "                    'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +
      "                ]" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label3'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +    // the 000000000000 account is not valid, so is not included
      "            }," +
      "            'Action':'SQS:DeleteMessage'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }" +
      "    ]" +
      "" +
      "}").replace(" ","").replace("'","\"");

    GetQueueAttributesResult getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    String queueArn = getQueueAttributesResult.getAttributes().get("QueueArn");
    String policy = policyTemplate.replace("%ACCOUNT_ID%", accountId).replace("%OTHER_ACCOUNT_ID%", otherAccountId).replace("%QUEUE_ARN%", queueArn);
    assertThat(getQueueAttributesResult.getAttributes().get("Policy").equals(policy), "Policies should match");
  }

  @Test
  public void testAddPermissionsFromExistingOneStatementPolicy() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermissionsFromOneStatementPolicy");
    String queueName = "queue_name_add_permissions_from_one_statement_policy";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String otherQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> otherPathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(otherQueueUrl).getPath()));
    String otherAccountId = otherPathParts.get(0);

    GetQueueAttributesResult getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    String queueArn = getQueueAttributesResult.getAttributes().get("QueueArn");

    String existingPolicy = new String("{" +
      "" +
      "    'Version':'2012-10-17'," +
      "    'Id':'mypolicy'," +
      "    'Statement':{" +
      "        'Sid':'sid1'," +
      "        'Effect':'Deny'," +
      "        'Principal':{" +
      "            'AWS':[" +
      "                'arn:aws:iam::111122223333:role/role1'," +
      "                'arn:aws:iam::111122223333:user/username1'" +
      "            ]" +
      "        }," +
      "        'Action':'sqs:*'," +
      "        'Resource':'%QUEUE_ARN%'" +
      "    }" +
      "" +
      "}").replace(" ","").replace("'","\"").replace("%QUEUE_ARN%", queueArn);

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy", existingPolicy));
    
    accountSQSClient.addPermission(queueUrl, "label1", Lists.newArrayList(accountId), ImmutableList.of(
      "*", "SendMessage", "ReceiveMessage", "DeleteMessage", "ChangeMessageVisibility", "GetQueueAttributes",
      "GetQueueUrl", "ListDeadLetterSourceQueues", "PurgeQueue"
    ));

    accountSQSClient.addPermission(queueUrl, "label2", Lists.newArrayList(accountId, otherAccountId), ImmutableList.of(
      "*", "SendMessage", "ReceiveMessage"));

    accountSQSClient.addPermission(queueUrl, "label3", Lists.newArrayList("000000000000", otherAccountId), ImmutableList.of(
      "DeleteMessage"));

    String policyTemplate = new String("{" +
      "" +
      "    'Version':'2012-10-17'," +
      "    'Id':'mypolicy'," +
      "    'Statement':[" +
      "        {" +
      "            'Sid':'sid1'," +
      "            'Effect':'Deny'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label1'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%ACCOUNT_ID%:root'" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'," +
      "                'SQS:DeleteMessage'," +
      "                'SQS:ChangeMessageVisibility'," +
      "                'SQS:GetQueueAttributes'," +
      "                'SQS:GetQueueUrl'," +
      "                'SQS:ListDeadLetterSourceQueues'," +
      "                'SQS:PurgeQueue'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::%ACCOUNT_ID%:root'," +
      "                    'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +
      "                ]" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label3'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" + // the 000000000000 account is not valid, so is not included
      "            }," +
      "            'Action':'SQS:DeleteMessage'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }" +
      "    ]" +
      "" +
      "}").replace(" ","").replace("'","\"");

    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    String policy = policyTemplate.replace("%ACCOUNT_ID%", accountId).replace("%OTHER_ACCOUNT_ID%", otherAccountId).replace("%QUEUE_ARN%", queueArn);
    assertThat(getQueueAttributesResult.getAttributes().get("Policy").equals(policy), "Policies should match");
  }

  @Test
  public void testAddPermissionsFromExistingTwoStatementPolicy() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermissionsFromTwoStatementPolicy");
    String queueName = "queue_name_add_permissions_from_two_statement_policy";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String otherQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> otherPathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(otherQueueUrl).getPath()));
    String otherAccountId = otherPathParts.get(0);

    GetQueueAttributesResult getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    String queueArn = getQueueAttributesResult.getAttributes().get("QueueArn");

    String existingPolicy = new String("{" +
      "" +
      "    'Version':'2012-10-17'," +
      "    'Id':'mypolicy'," +
      "    'Statement':[" +
      "        {" +
      "            'Sid':'sid1'," +
      "            'Effect':'Deny'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'sid2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }" +
      "    ]" +
      "" +
      "}").replace(" ","").replace("'","\"").replace("%QUEUE_ARN%", queueArn);

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy", existingPolicy));

    accountSQSClient.addPermission(queueUrl, "label1", Lists.newArrayList(accountId), ImmutableList.of(
      "*", "SendMessage", "ReceiveMessage", "DeleteMessage", "ChangeMessageVisibility", "GetQueueAttributes",
      "GetQueueUrl", "ListDeadLetterSourceQueues", "PurgeQueue"
    ));

    accountSQSClient.addPermission(queueUrl, "label2", Lists.newArrayList(accountId, otherAccountId), ImmutableList.of(
      "*", "SendMessage", "ReceiveMessage"));

    accountSQSClient.addPermission(queueUrl, "label3", Lists.newArrayList("000000000000", otherAccountId), ImmutableList.of(
      "DeleteMessage"));

    String policyTemplate = new String("{" +
      "" +
      "    'Version':'2012-10-17'," +
      "    'Id':'mypolicy'," +
      "    'Statement':[" +
      "        {" +
      "            'Sid':'sid1'," +
      "            'Effect':'Deny'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'sid2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label1'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%ACCOUNT_ID%:root'" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'," +
      "                'SQS:DeleteMessage'," +
      "                'SQS:ChangeMessageVisibility'," +
      "                'SQS:GetQueueAttributes'," +
      "                'SQS:GetQueueUrl'," +
      "                'SQS:ListDeadLetterSourceQueues'," +
      "                'SQS:PurgeQueue'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::%ACCOUNT_ID%:root'," +
      "                    'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +
      "                ]" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label3'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +  // the 000000000000 account is not valid, so is not included
      "            }," +
      "            'Action':'SQS:DeleteMessage'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }" +
      "    ]" +
      "" +
      "}").replace(" ","").replace("'","\"");

    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    String policy = policyTemplate.replace("%ACCOUNT_ID%", accountId).replace("%OTHER_ACCOUNT_ID%", otherAccountId).replace("%QUEUE_ARN%", queueArn);
    assertThat(getQueueAttributesResult.getAttributes().get("Policy").equals(policy), "Policies should match");
  }

  @Test
  public void testRemovePermissionNonExistentAccountQueueUrl() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testRemovePermissionNonExistentAccount");
    String queueName = "queue_name_remove_permission_nonexistent_account_url";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    try {
      accountSQSClient.removePermission(queueUrl.replace(accountId, "000000000000"), "label");
      assertThat(false, "Should fail removing permission on a queue url from a non-existent user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Correctly fail removing permission on a queue url from a non-existent user");
    }
  }

  @Test
  public void testRemovePermissionOtherAccountQueueUrl() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testRemovePermissionOtherAccount");
    String queueName = "queue_name_remove_permission_other_account_url";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String otherAccountQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    try {
      accountSQSClient.removePermission(otherAccountQueueUrl, "label");
      assertThat(false, "Should fail removing permission on a queue url from a different user");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail removing permission on a queue url from a different user");
    }
  }

  @Test
  public void testRemovePermissionNonExistentQueueUrl() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testRemovePermissionOtherAccount");
    String queueName = "queue_name_remove_permission_nonexistent_queue_url";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    try {
      accountSQSClient.removePermission(queueUrl + "-bogus", "label");
      assertThat(false, "Should fail removing permission on a nonexistent queue url");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail removing permission on a nonexistent queue url");
    }
  }

  @Test
  public void testRemovePermissionBadLabel() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testRemovePermissionBadLabel");
    String queueName = "queue_name_remove_permission_bad_label";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);


    // Specifically test: null, empty, too long, invalid characters
    Map<Optional<String>, String> badLabelsAndReason = ImmutableMap.of(
      Optional.absent(), "is null",
      Optional.of(""), "is empty",
      Optional.of(Strings.repeat("X", 1 + MAX_LABEL_LENGTH_CHARS)), "is too long",
      Optional.of("@#$@#$@#$!"), "contains invalid characters");
    for (Optional<String> badLabelOptional: badLabelsAndReason.keySet()) {
      String badLabel = badLabelOptional.isPresent() ? badLabelOptional.get() : null;
      try {
        accountSQSClient.removePermission(queueUrl, badLabel);
        assertThat(false, "Should fail removing permission on a queue with a label that " + badLabelsAndReason.get(badLabel));
      } catch (AmazonServiceException e) {
        assertThat(e.getStatusCode() == 400, "Correctly fail removing permission on a queue with a label that " + badLabelsAndReason.get(badLabel));
      }
    }

    // Now test duplicate remove 'bad' cases after a hopefully successful case, in the following situations:
    // 1) single statement, no matching label
    // 2) single statement, matching label
    // 3) two statements, no matching label
    // 4) two statements, matching label
    // 5) no policy

    GetQueueAttributesResult getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    String queueArn = getQueueAttributesResult.getAttributes().get("QueueArn");

    String oneStatementTemplate = new String("{" +
      "" +
      "    'Version':'2012-10-17'," +
      "    'Id':'mypolicy'," +
      "    'Statement':{" +
      "        'Sid':'%LABEL%'," +
      "        'Effect':'Deny'," +
      "        'Principal':{" +
      "            'AWS':[" +
      "                'arn:aws:iam::111122223333:role/role1'," +
      "                'arn:aws:iam::111122223333:user/username1'" +
      "            ]" +
      "        }," +
      "        'Action':'sqs:*'," +
      "        'Resource':'%QUEUE_ARN%'" +
      "    }" +
      "" +
      "}").replace(" ","").replace("'","\"").replace("%QUEUE_ARN%", queueArn);

    String twoStatementTemplate = new String("{" +
      "" +
      "    'Version':'2012-10-17'," +
      "    'Id':'mypolicy'," +
      "    'Statement':[" +
      "        {" +
      "            'Sid':'%LABEL_1%'," +
      "            'Effect':'Deny'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'%LABEL_2%'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }" +
      "    ]" +
      "" +
      "}").replace(" ","").replace("'","\"").replace("%QUEUE_ARN%", queueArn);

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy", oneStatementTemplate.replace("%LABEL%", "label")));
    accountSQSClient.removePermission(queueUrl, "label");
    try {
      accountSQSClient.removePermission(queueUrl, "label");
      assertThat(false, "Should fail removing permission on a queue with a label that doesn't exist");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail removing permission on a queue with a label that doesn't exist");
    }

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy", oneStatementTemplate.replace("%LABEL%", "not_label")));
    try {
      accountSQSClient.removePermission(queueUrl, "label");
      assertThat(false, "Should fail removing permission on a queue with a label that doesn't exist");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail removing permission on a queue with a label that doesn't exist");
    }

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy", twoStatementTemplate.replace("%LABEL_1%", "label").replace("%LABEL_2%", "not_label")));
    accountSQSClient.removePermission(queueUrl, "label");
    try {
      accountSQSClient.removePermission(queueUrl, "label");
      assertThat(false, "Should fail removing permission on a queue with a label that doesn't exist");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail removing permission on a queue with a label that doesn't exist");
    }

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy", twoStatementTemplate.replace("%LABEL_2%", "label").replace("%LABEL_1%", "not_label")));
    accountSQSClient.removePermission(queueUrl, "label");
    try {
      accountSQSClient.removePermission(queueUrl, "label");
      assertThat(false, "Should fail removing permission on a queue with a label that doesn't exist");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail removing permission on a queue with a label that doesn't exist");
    }

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy", twoStatementTemplate.replace("%LABEL_1%", "label").replace("%LABEL_2%", "label")));
    accountSQSClient.removePermission(queueUrl, "label");
    try {
      accountSQSClient.removePermission(queueUrl, "label");
      assertThat(false, "Should fail removing permission on a queue with a label that doesn't exist");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail removing permission on a queue with a label that doesn't exist");
    }
    assertThat(!getQueueAttributesResult.getAttributes().containsKey("Policy"), "Policy should not exist");
    try {
      accountSQSClient.removePermission(queueUrl, "label");
      assertThat(false, "Should fail removing permission on a queue with a label that doesn't exist");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Correctly fail removing permission on a queue with a label that doesn't exist");
    }
  }


  @Test
  public void testRemovePermissionsSuccessful() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testRemovePermissionsSuccessful");
    String queueName = "queue_name_remove_permissions_success";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);
    String otherQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> otherPathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(otherQueueUrl).getPath()));
    String otherAccountId = otherPathParts.get(0);

    GetQueueAttributesResult getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    String queueArn = getQueueAttributesResult.getAttributes().get("QueueArn");

    String existingPolicy = new String("{" +
      "" +
      "    'Version':'2012-10-17'," +
      "    'Id':'mypolicy'," +
      "    'Statement':[" +
      "        {" +
      "            'Sid':'sid1'," +
      "            'Effect':'Deny'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'sid2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label1'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%ACCOUNT_ID%:root'" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'," +
      "                'SQS:DeleteMessage'," +
      "                'SQS:ChangeMessageVisibility'," +
      "                'SQS:GetQueueAttributes'," +
      "                'SQS:GetQueueUrl'," +
      "                'SQS:ListDeadLetterSourceQueues'," +
      "                'SQS:PurgeQueue'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::%ACCOUNT_ID%:root'," +
      "                    'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +
      "                ]" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label3'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +
      "            }," +
      "            'Action':'SQS:DeleteMessage'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }" +
      "    ]" +
      "" +
      "}").replace(" ","").replace("'","\"").replace("%QUEUE_ARN%", queueArn).replace("%ACCOUNT_ID%", accountId).replace("%OTHER_ACCOUNT_ID%", otherAccountId);

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy", existingPolicy));


    // remove first item
    accountSQSClient.removePermission(queueUrl, "sid1");

    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));

    String newPolicy = new String("{" +
      "" +
      "    'Version':'2012-10-17'," +
      "    'Id':'mypolicy'," +
      "    'Statement':[" +
      "        {" +
      "            'Sid':'sid2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label1'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%ACCOUNT_ID%:root'" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'," +
      "                'SQS:DeleteMessage'," +
      "                'SQS:ChangeMessageVisibility'," +
      "                'SQS:GetQueueAttributes'," +
      "                'SQS:GetQueueUrl'," +
      "                'SQS:ListDeadLetterSourceQueues'," +
      "                'SQS:PurgeQueue'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::%ACCOUNT_ID%:root'," +
      "                    'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +
      "                ]" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label3'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +
      "            }," +
      "            'Action':'SQS:DeleteMessage'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }" +
      "    ]" +
      "" +
      "}").replace(" ","").replace("'","\"").replace("%QUEUE_ARN%", queueArn).replace("%ACCOUNT_ID%", accountId).replace("%OTHER_ACCOUNT_ID%", otherAccountId);

    assertThat(getQueueAttributesResult.getAttributes().get("Policy").equals(newPolicy), "Policies should match");

    // delete the last item
    accountSQSClient.removePermission(queueUrl, "label3");

    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));

    newPolicy = new String("{" +
      "" +
      "    'Version':'2012-10-17'," +
      "    'Id':'mypolicy'," +
      "    'Statement':[" +
      "        {" +
      "            'Sid':'sid2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label1'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':'arn:aws:iam::%ACCOUNT_ID%:root'" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'," +
      "                'SQS:DeleteMessage'," +
      "                'SQS:ChangeMessageVisibility'," +
      "                'SQS:GetQueueAttributes'," +
      "                'SQS:GetQueueUrl'," +
      "                'SQS:ListDeadLetterSourceQueues'," +
      "                'SQS:PurgeQueue'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::%ACCOUNT_ID%:root'," +
      "                    'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +
      "                ]" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }" +
      "    ]" +
      "" +
      "}").replace(" ","").replace("'","\"").replace("%QUEUE_ARN%", queueArn).replace("%ACCOUNT_ID%", accountId).replace("%OTHER_ACCOUNT_ID%", otherAccountId);

    assertThat(getQueueAttributesResult.getAttributes().get("Policy").equals(newPolicy), "Policies should match");

    // delete the middle item
    accountSQSClient.removePermission(queueUrl, "label1");

    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    newPolicy = new String("{" +
      "" +
      "    'Version':'2012-10-17'," +
      "    'Id':'mypolicy'," +
      "    'Statement':[" +
      "        {" +
      "            'Sid':'sid2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::111122223333:role/role1'," +
      "                    'arn:aws:iam::111122223333:user/username1'" +
      "                ]" +
      "            }," +
      "            'Action':'sqs:*'," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }," +
      "        {" +
      "            'Sid':'label2'," +
      "            'Effect':'Allow'," +
      "            'Principal':{" +
      "                'AWS':[" +
      "                    'arn:aws:iam::%ACCOUNT_ID%:root'," +
      "                    'arn:aws:iam::%OTHER_ACCOUNT_ID%:root'" +
      "                ]" +
      "            }," +
      "            'Action':[" +
      "                'SQS:*'," +
      "                'SQS:SendMessage'," +
      "                'SQS:ReceiveMessage'" +
      "            ]," +
      "            'Resource':'%QUEUE_ARN%'" +
      "        }" +
      "    ]" +
      "" +
      "}").replace(" ","").replace("'","\"").replace("%QUEUE_ARN%", queueArn).replace("%ACCOUNT_ID%", accountId).replace("%OTHER_ACCOUNT_ID%", otherAccountId);

    assertThat(getQueueAttributesResult.getAttributes().get("Policy").equals(newPolicy), "Policies should match");

    // now delete the rest, make sure no policy
    accountSQSClient.removePermission(queueUrl, "sid2");
    accountSQSClient.removePermission(queueUrl, "label2");
    getQueueAttributesResult = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All"));
    assertThat(!getQueueAttributesResult.getAttributes().containsKey("Policy"), "Policy should not exist");

  }


  private int getLocalConfigInt(String propertySuffixInCapsAndUnderscores) throws IOException {
    String propertyName = "services.simplequeue." + propertySuffixInCapsAndUnderscores.toLowerCase();
    return Integer.parseInt(getConfigProperty(LOCAL_EUCTL_FILE, propertyName));
  }
}
