package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.GetQueueUrlRequest;
import com.amazonaws.services.sqs.model.ListDeadLetterSourceQueuesRequest;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.createUser;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.getUserCreds;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSStatusCodesForNonexistentQueues {


  private String account;
  private String otherAccount;
  private AmazonSQS accountSQSClient;
  private AmazonSQS accountUserSQSClient;
  private AmazonSQS otherAccountSQSClient;
  private AmazonSQS otherAccountUserSQSClient;

  private String accountId;
  private String queueUrl;
  private String bogusQueueName;
  private String bogusQueueUrl;
  private Collection<NamedAmazonSQS> clients;


  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-a-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
      AWSCredentials accountCredentials = getUserCreds(account, "admin");
      createUser(account, "user");
      accountUserSQSClient = getSqsClientWithNewAccount(account, "user");
      otherAccount = "sqs-account-b-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(otherAccount);
      otherAccountSQSClient = getSqsClientWithNewAccount(otherAccount, "admin");
      AWSCredentials otherAccountCredentials = getUserCreds(otherAccount, "admin");
      createUser(otherAccount, "user");
      otherAccountUserSQSClient = getSqsClientWithNewAccount(otherAccount, "user");

      String queueName = "queue_name_status_codes_nonexistent_queue";
      queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
      bogusQueueName = queueName + "-bogus";
      bogusQueueUrl = queueUrl + "-bogus";
      List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
      accountId = pathParts.get(0);
      String otherQueueUrl = otherAccountSQSClient.createQueue(queueName).getQueueUrl();
      List<String> otherPathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(otherQueueUrl).getPath()));
      final String otherAccountId = otherPathParts.get(0);

      clients = Sets.newHashSet();
      clients.add(new NamedAmazonSQS("Main Account", accountSQSClient));
      clients.add(new NamedAmazonSQS("Main User", accountUserSQSClient));
      clients.add(new NamedAmazonSQS("Other Account", otherAccountSQSClient));
      clients.add(new NamedAmazonSQS("Other User", otherAccountUserSQSClient));

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

  public static class NamedAmazonSQS {
    AmazonSQS amazonSQS;
    String name;

    public AmazonSQS getAmazonSQS() {
      return amazonSQS;
    }

    public void setAmazonSQS(AmazonSQS amazonSQS) {
      this.amazonSQS = amazonSQS;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public NamedAmazonSQS(String name, AmazonSQS amazonSQS) {
      this.name = name;
      this.amazonSQS = amazonSQS;
    }
  }

    @Test
  public void testAddPermission() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAddPermission");
    Command command = new Command("AddPermission") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        sqs.addPermission(bogusQueueUrl, "label", Collections.singletonList(accountId), Collections.singletonList("SendMessage"));
      }
    };

    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testChangeMessageVisibility() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibility");
    Command command = new Command("ChangeMessageVisibility") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        sqs.changeMessageVisibility(bogusQueueUrl, "blah", 0);
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testChangeMessageVisibilityBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testChangeMessageVisibilityBatch");
    Command command = new Command("ChangeMessageVisibilityBatch") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        List<ChangeMessageVisibilityBatchRequestEntry> x = new ArrayList<ChangeMessageVisibilityBatchRequestEntry>();
        ChangeMessageVisibilityBatchRequestEntry e = new ChangeMessageVisibilityBatchRequestEntry();
        e.setReceiptHandle("blah");
        e.setVisibilityTimeout(0);
        e.setId("id");
        x.add(e);
        sqs.changeMessageVisibilityBatch(bogusQueueUrl, x);
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testDeleteMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessage");
    Command command = new Command("DeleteMessage") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        sqs.deleteMessage(bogusQueueUrl, "blah");
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testDeleteMessageBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteMessageBatch");
    Command command = new Command("DeleteMessageBatch") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        List<DeleteMessageBatchRequestEntry> x = new ArrayList<DeleteMessageBatchRequestEntry>();
        DeleteMessageBatchRequestEntry e = new DeleteMessageBatchRequestEntry();
        e.setReceiptHandle("blah");
        e.setId("id");
        x.add(e);
        sqs.deleteMessageBatch(bogusQueueUrl, x);
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testDeleteQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testDeleteQueue");
    Command command = new Command("DeleteQueue") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        sqs.deleteQueue(bogusQueueUrl);
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testGetQueueAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueAttributes");
    Command command = new Command("GetQueueAttributes") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        sqs.getQueueAttributes(bogusQueueUrl, Collections.singletonList("All"));
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testGetQueueUrl() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testGetQueueUrl");
    Command command = new Command("GetQueueUrl") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        GetQueueUrlRequest getQueueUrlRequest = new GetQueueUrlRequest();
        getQueueUrlRequest.setQueueOwnerAWSAccountId(accountId);
        getQueueUrlRequest.setQueueName(bogusQueueName);
        sqs.getQueueUrl(getQueueUrlRequest);
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testListDeadLetterSourceQueues() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testListDeadLetterSourceQueues");
    Command command =       new Command("ListDeadLetterSourceQueues") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        ListDeadLetterSourceQueuesRequest x = new ListDeadLetterSourceQueuesRequest();
        x.setQueueUrl(bogusQueueUrl);
        sqs.listDeadLetterSourceQueues(x);
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testPurgeQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testPurgeQueue");
    Command command = new Command("PurgeQueue") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        PurgeQueueRequest x = new PurgeQueueRequest();
        x.setQueueUrl(bogusQueueUrl);
        sqs.purgeQueue(x);
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testReceiveMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testReceiveMessage");
    Command command = new Command("ReceiveMessage") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        sqs.receiveMessage(bogusQueueUrl);
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testRemovePermission() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testRemovePermission");
    Command command = new Command("RemovePermission") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        sqs.removePermission(bogusQueueUrl, "label");
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testSendMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessage");
    Command command = new Command("SendMessage") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        sqs.sendMessage(bogusQueueUrl, "hello");
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testSendMessageBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSendMessageBatch");
    Command command = new Command("SendMessageBatch") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        List<SendMessageBatchRequestEntry> x = new ArrayList<SendMessageBatchRequestEntry>();
        SendMessageBatchRequestEntry e = new SendMessageBatchRequestEntry();
        e.setMessageBody("hello");
        e.setId("id");
        x.add(e);
        sqs.sendMessageBatch(bogusQueueUrl, x);
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  @Test
  public void testSetQueueAttributes() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testSetQueueAttributes");
    Command command = new Command("SetQueueAttributes") {
      @Override
      public void runCommand(AmazonSQS sqs) throws AmazonServiceException {
        sqs.setQueueAttributes(bogusQueueUrl, Collections.singletonMap("VisibilityTimeout", "0"));
      }
    };
    for (NamedAmazonSQS client: clients) {
      testClientWithCommand(command, client);
    }
  }

  private static final int SUCCESS = 200;
  private static final int QUEUE_DOES_NOT_EXIST = 400;
  private static final int ACCESS_DENIED = 403;

  public abstract static class Command {
    public String getName() {
      return name;
    }

    private String name;

    protected Command(String name) {
      this.name = name;
    }

    public final int getStatusFromCommand(AmazonSQS sqs) {
      try {
        runCommand(sqs);
        return SUCCESS;
      } catch (AmazonServiceException e) {
        return e.getStatusCode();
      }
    }
    public abstract void runCommand(AmazonSQS sqs) throws AmazonServiceException;
  }

  private void testClientWithCommand(Command command, NamedAmazonSQS namedAmazonSQS) {
    int expectedCode;
    // some commands and clients should return ACCESS_DENIED
    // non "shared-queue" commands with cross-account credentials
    if (ImmutableSet.of("AddPermission", "DeleteQueue", "RemovePermission", "SetQueueAttributes").contains(command.getName()) &&
      ImmutableSet.of("Other Account", "Other User").contains(namedAmazonSQS.getName())
      ) {
      expectedCode = ACCESS_DENIED;
    } else {
      expectedCode = QUEUE_DOES_NOT_EXIST;
    }
    int actualCode = command.getStatusFromCommand(namedAmazonSQS.getAmazonSQS());
    assertThat(expectedCode == actualCode, "Calling " + command.getName() + " with client " + namedAmazonSQS.getName() + ", status code was " + actualCode + ", expected " + expectedCode);
  }



}