package com.eucalyptus.tests.awssdk;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClient;
import com.amazonaws.services.identitymanagement.model.CreateRoleRequest;
import com.amazonaws.services.identitymanagement.model.CreateRoleResult;
import com.amazonaws.services.identitymanagement.model.PutRolePolicyRequest;
import com.amazonaws.services.identitymanagement.model.PutUserPolicyRequest;
import com.amazonaws.services.identitymanagement.model.Role;
import com.amazonaws.services.identitymanagement.model.User;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClient;
import com.amazonaws.services.securitytoken.model.AssumeRoleRequest;
import com.amazonaws.services.securitytoken.model.Credentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.beust.jcommander.internal.Maps;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.eucalyptus.tests.awssdk.N4j.IAM_ENDPOINT;
import static com.eucalyptus.tests.awssdk.N4j.SQS_ENDPOINT;
import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.createUser;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getUserCreds;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSSenderId {

  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AWSCredentials accountCredentials;

  private AmazonSQS otherAccountSQSClient;
  private AWSCredentials otherAccountCredentials;

  
  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-a-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(account);

      accountCredentials = getUserCreds(account, "admin");
      accountSQSClient = new AmazonSQSClient(accountCredentials);
      accountSQSClient.setEndpoint(SQS_ENDPOINT);

      createUser(account, "user1ac1");
      createUser(account, "user2ac1");

      otherAccount = "sqs-account-b-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(otherAccount);
      otherAccountCredentials = getUserCreds(otherAccount, "admin");
      otherAccountSQSClient = new AmazonSQSClient(otherAccountCredentials);
      otherAccountSQSClient.setEndpoint(SQS_ENDPOINT);

      createUser(otherAccount, "user1ac2");
      createUser(otherAccount, "user2ac2");

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

  public static String everythingPolicy(String stmtId) {
    return "{\"Statement\": [{\"Action\": [\"*\"], \"Resource\": \"*\", \"Effect\": \"Allow\", \"Sid\": \""+stmtId+"\"}]}";
  }
  @Test
  public void testSenderId() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSenderId");
    String queueName = "queue_name_test_sender_id";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    String queueArn = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("QueueArn")).getAttributes().get("QueueArn");

    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    String accountId = pathParts.get(0);

    AmazonIdentityManagement accountIAMClient = new AmazonIdentityManagementClient(accountCredentials);
    accountIAMClient.setEndpoint(IAM_ENDPOINT);
    Map<String, User> accountUsers = Maps.newHashMap();
    for (User user: accountIAMClient.listUsers().getUsers()) {
      accountUsers.put(user.getUserName(), user);
    }

    AmazonIdentityManagement otherAccountIAMClient = new AmazonIdentityManagementClient(otherAccountCredentials);
    otherAccountIAMClient.setEndpoint(IAM_ENDPOINT);
    Map<String, User> otherAccountUsers = Maps.newHashMap();
    for (User user: otherAccountIAMClient.listUsers().getUsers()) {
      otherAccountUsers.put(user.getUserName(), user);
    }

    String rolePolicyDocument = new String("{\n" +
      "  \"Version\": \"2012-10-17\",\n" +
      "  \"Statement\": [\n" +
      "    {\n" +
      "      \"Effect\": \"Allow\",\n" +
      "      \"Principal\": {\n" +
      "        \"AWS\": [\"USER1AC2\",\"ADMIN\"]\n" +
      "      },\n" +
      "      \"Action\": \"sts:AssumeRole\"\n" +
      "    }\n" +
      "  ]\n" +
      "}\n").replace("USER1AC2", otherAccountUsers.get("user1ac2").getArn())
      .replace("ADMIN", otherAccountUsers.get("admin").getArn());
    CreateRoleRequest createRoleRequest = new CreateRoleRequest();
    createRoleRequest.setAssumeRolePolicyDocument(rolePolicyDocument);
    createRoleRequest.setRoleName("role1");
    CreateRoleResult createRoleResult = accountIAMClient.createRole(createRoleRequest);
    Role role = createRoleResult.getRole();
    PutRolePolicyRequest putRolePolicyRequest = new PutRolePolicyRequest();
    putRolePolicyRequest.setRoleName("role1");
    putRolePolicyRequest.setPolicyName("everything");
    putRolePolicyRequest.setPolicyDocument(everythingPolicy("roleStmt"));
    accountIAMClient.putRolePolicy(putRolePolicyRequest);

    // give all users all permissions
    PutUserPolicyRequest putUserPolicyRequest = new PutUserPolicyRequest();
    putUserPolicyRequest.setPolicyName("everything");

    putUserPolicyRequest.setPolicyDocument(everythingPolicy("user1ac1"));
    putUserPolicyRequest.setUserName("user1ac1");
    accountIAMClient.putUserPolicy(putUserPolicyRequest);

    putUserPolicyRequest.setPolicyDocument(everythingPolicy("user2ac1"));
    putUserPolicyRequest.setUserName("user2ac1");
    accountIAMClient.putUserPolicy(putUserPolicyRequest);

    putUserPolicyRequest.setPolicyDocument(everythingPolicy("user1ac2"));
    putUserPolicyRequest.setUserName("user1ac2");
    otherAccountIAMClient.putUserPolicy(putUserPolicyRequest);

    putUserPolicyRequest.setPolicyDocument(everythingPolicy("user2ac2"));
    putUserPolicyRequest.setUserName("user2ac2");
    otherAccountIAMClient.putUserPolicy(putUserPolicyRequest);

    String queuePolicy = new String("{\n" +
      "  \"Version\": \"2012-10-17\",\n" +
      "  \"Id\": \"QUEUE_ARN/SQSDefaultPolicy\",\n" +
      "  \"Statement\": [\n" +
      "    {\n" +
      "      \"Sid\": \"AllowAll\",\n" +
      "      \"Effect\": \"Allow\",\n" +
      "      \"Principal\": {\"AWS\": [\"ACCOUNT_ID\",\"USER2AC2\",\"ADMIN\"]},\n" +
      "      \"Action\": \"SQS:*\",\n" +
      "      \"Resource\": \"QUEUE_ARN\"\n" +
      "    }\n" +
      "  ]\n" +
      "}\n").replace("QUEUE_ARN", queueArn)
        .replace("ACCOUNT_ID", accountId)
        .replace("USER2AC2", otherAccountUsers.get("user2ac2").getArn())
        .replace("ADMIN", otherAccountUsers.get("admin").getArn());

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("Policy", queuePolicy));

    // send messages from each user

    String accountMessageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    String accountUser1MessageId = ((AmazonSQS) new AmazonSQSClient(getUserCreds(account, "user1ac1"))
      .withEndpoint(SQS_ENDPOINT)).sendMessage(queueUrl, "hello").getMessageId();
    String accountUser2MessageId = ((AmazonSQS) new AmazonSQSClient(getUserCreds(account, "user2ac1"))
      .withEndpoint(SQS_ENDPOINT)).sendMessage(queueUrl, "hello").getMessageId();

    String otherAccountMessageId = otherAccountSQSClient.sendMessage(queueUrl, "hello").getMessageId();
    String otherAccountUser2MessageId = ((AmazonSQS) new AmazonSQSClient(getUserCreds(otherAccount, "user2ac2"))
      .withEndpoint(SQS_ENDPOINT)).sendMessage(queueUrl, "hello").getMessageId();

    // now send messages after we assume a role
    // HACK!
    final String STS_ENDPOINT = SQS_ENDPOINT.replace("simplequeue","sts");
    AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest();
    assumeRoleRequest.setRoleArn(role.getArn());
    assumeRoleRequest.setRoleSessionName("session1");

    Credentials otherAccountRoleCredentials = ((AWSSecurityTokenService) new AWSSecurityTokenServiceClient(otherAccountCredentials)
      .withEndpoint(STS_ENDPOINT)).assumeRole(assumeRoleRequest).getCredentials();

    String otherAccountRoleMessageId = ((AmazonSQS) new AmazonSQSClient(new BasicSessionCredentials(otherAccountRoleCredentials.getAccessKeyId(), otherAccountRoleCredentials.getSecretAccessKey(), otherAccountRoleCredentials.getSessionToken()))
      ).sendMessage(queueUrl, "hello").getMessageId();

    assumeRoleRequest.setRoleSessionName("session2");

    Credentials otherAccountUser1RoleCredentials = ((AWSSecurityTokenService) new AWSSecurityTokenServiceClient(getUserCreds(otherAccount, "user1ac2"))
      .withEndpoint(STS_ENDPOINT)).assumeRole(assumeRoleRequest).getCredentials();

    String otherAccountUser1RoleMessageId = ((AmazonSQS) new AmazonSQSClient(new BasicSessionCredentials(otherAccountUser1RoleCredentials.getAccessKeyId(), otherAccountUser1RoleCredentials.getSecretAccessKey(), otherAccountUser1RoleCredentials.getSessionToken()))
    ).sendMessage(queueUrl, "hello").getMessageId();

    Set<String> messageIds = ImmutableSet.of(
      accountMessageId,
      accountUser1MessageId,
      accountUser2MessageId,
      otherAccountMessageId,
      otherAccountUser2MessageId,
      otherAccountRoleMessageId,
      otherAccountUser1RoleMessageId
    );

    long timeout = 120000L;
    Map<String, Message> messages = Maps.newHashMap();
    long startTime = System.currentTimeMillis();
    while (!messageIds.equals(messages.keySet()) && System.currentTimeMillis() - startTime < timeout) {
      ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest();
      receiveMessageRequest.setAttributeNames(Collections.singletonList("SenderId"));
      receiveMessageRequest.setQueueUrl(queueUrl);
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(receiveMessageRequest);
      for (Message message : receiveMessageResult.getMessages()) {
        messages.put(message.getMessageId(), message);
      }
    }
    if (!messageIds.equals(messages.keySet())) {
      assertThat(false, "Not all messages received before timeout");
    }

    assertEquals(accountUsers.get("admin").getUserId(), messages.get(accountMessageId).getAttributes().get("SenderId"));
    assertEquals(accountUsers.get("user1ac1").getUserId(), messages.get(accountUser1MessageId).getAttributes().get("SenderId"));
    assertEquals(accountUsers.get("user2ac1").getUserId(), messages.get(accountUser2MessageId).getAttributes().get("SenderId"));
    assertEquals(otherAccountUsers.get("admin").getUserId(), messages.get(otherAccountMessageId).getAttributes().get("SenderId"));
    assertEquals(otherAccountUsers.get("user2ac2").getUserId(), messages.get(otherAccountUser2MessageId).getAttributes().get("SenderId"));
    assertEquals(role.getRoleId() + ":session1", messages.get(otherAccountRoleMessageId).getAttributes().get("SenderId"));
    assertEquals(role.getRoleId() + ":session2", messages.get(otherAccountUser1RoleMessageId).getAttributes().get("SenderId"));
  }

  private void assertEquals(String s, String t) {
    assertThat(Objects.equals(s, t), "Expecting: " + s + ", received " + t);
  }

}
