package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceClient;
import com.amazonaws.Request;
import com.amazonaws.handlers.RequestHandler2;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.AddPermissionRequest;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import com.amazonaws.services.sqs.model.ChangeMessageVisibilityRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequest;
import com.amazonaws.services.sqs.model.DeleteMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesResult;
import com.amazonaws.services.sqs.model.ListDeadLetterSourceQueuesRequest;
import com.amazonaws.services.sqs.model.ListDeadLetterSourceQueuesResult;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.PurgeQueueRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.RemovePermissionRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSQueueUrlBinding {

  private String account;
  private String otherAccount;

  private AmazonSQS accountSQSClient;
  private AmazonSQS otherAccountSQSClient;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
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
  public void testQueueUrlBindingDeleteQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingDeleteQueue");
    String queueName1 = "queue_name_test_binding_delete_queue_1";
    String queueUrl1 = accountSQSClient.createQueue(queueName1).getQueueUrl();

    String queueName2 = "queue_name_test_binding_delete_queue_2";
    String queueUrl2 = accountSQSClient.createQueue(queueName2).getQueueUrl();

    // make sure we have two queues
    assertThat(accountSQSClient.listQueues("queue_name_test_binding_delete_queue").getQueueUrls().size() == 2, "We should have two queues");

    // delete a queue using the Request Parameter, then the original way
    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl1) {

      @Override
      public Object doOperation() {
        DeleteQueueRequest deleteQueueRequest = new DeleteQueueRequest();
        getSqsClient().deleteQueue(deleteQueueRequest);
        return null;
      }
    }.doWrappedOperation();
    assertThat(accountSQSClient.listQueues("queue_name_test_binding_delete_queue").getQueueUrls().size() == 1, "We should have one queue");
    accountSQSClient.deleteQueue(queueUrl2);
    assertThat(accountSQSClient.listQueues("queue_name_test_binding_delete_queue").getQueueUrls().size() == 0, "We should have no queues");
  }

  @Test
  public void testQueueUrlBindingAddPermission() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingAddPermission");
    String queueName = "queue_name_test_binding_add_permission";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    final String accountId = pathParts.get(0);
    // now try add permission with a queue

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
        AddPermissionRequest addPermissionRequest = new AddPermissionRequest();
        addPermissionRequest.setActions(Collections.singletonList("SendMessage"));
        addPermissionRequest.setAWSAccountIds(Collections.singletonList(accountId));
        addPermissionRequest.setLabel("label");
        getSqsClient().addPermission(addPermissionRequest);
        return null;
      }
    }.doWrappedOperation();

    // Make sure we have a policy now
    assertThat(getPolicy(accountSQSClient, queueUrl) != null, "We have a policy after we add a permission");

    accountSQSClient.removePermission(queueUrl, "label");
    // now we should have no policy
    assertThat(getPolicy(accountSQSClient, queueUrl) == null, "We have no more policy after we remove the permission");

  }

  @Test
  public void testQueueUrlBindingRemovePermission() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingRemovePermission");
    String queueName = "queue_name_test_binding_remove_permission";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    List<String> pathParts = Lists.newArrayList(Splitter.on('/').omitEmptyStrings().split(new URL(queueUrl).getPath()));
    final String accountId = pathParts.get(0);
    // now try add permission with a queue

    AddPermissionRequest addPermissionRequest = new AddPermissionRequest();
    addPermissionRequest.setActions(Collections.singletonList("SendMessage"));
    addPermissionRequest.setAWSAccountIds(Collections.singletonList(accountId));
    addPermissionRequest.setLabel("label");
    addPermissionRequest.setQueueUrl(queueUrl);

    accountSQSClient.addPermission(addPermissionRequest);
    // Make sure we have a policy now
    assertThat(getPolicy(accountSQSClient, queueUrl) != null, "We have a policy after we add a permission");

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
        RemovePermissionRequest removePermissionRequest = new RemovePermissionRequest();
        removePermissionRequest.setLabel("label");
        getSqsClient().removePermission(removePermissionRequest);
        return null;
      }
    }.doWrappedOperation();
    // now we should have no policy
    assertThat(getPolicy(accountSQSClient, queueUrl) == null, "We have no more policy after we remove the permission");
  }

  private String getPolicy(AmazonSQS sqsClient, String queueUrl) {
    return sqsClient.getQueueAttributes(queueUrl, Collections.singletonList("All")).getAttributes().get("Policy");
  }

  @Test
  public void testQueueUrlBindingSendMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingSendMessage");
    String queueName = "queue_name_test_binding_send_message";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    assertThat(messageTotals(accountSQSClient, queueUrl) == 0, "There are no messages");

    accountSQSClient.sendMessage(queueUrl, "Hello");
    assertThat(messageTotals(accountSQSClient, queueUrl) == 1, "There is 1 message");

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
        SendMessageRequest sendMessageRequest = new SendMessageRequest();
        sendMessageRequest.setMessageBody("hello");
        return getSqsClient().sendMessage(sendMessageRequest);
      }
    }.doWrappedOperation();
    assertThat(messageTotals(accountSQSClient, queueUrl) == 2, "There are 2 messages");
  }

  @Test
  public void testQueueUrlBindingSendMessageBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingSendMessageBatch");
    String queueName = "queue_name_test_binding_send_message_batch";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    assertThat(messageTotals(accountSQSClient, queueUrl) == 0, "There are no messages");
    SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
    SendMessageBatchRequestEntry entry = new SendMessageBatchRequestEntry();
    entry.setMessageBody("body");
    entry.setId("id");
    sendMessageBatchRequest.getEntries().add(entry);
    sendMessageBatchRequest.setQueueUrl(queueUrl);
    accountSQSClient.sendMessageBatch(sendMessageBatchRequest);
    assertThat(messageTotals(accountSQSClient, queueUrl) == 1, "There is 1 message");

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
        SendMessageBatchRequest sendMessageBatchRequest = new SendMessageBatchRequest();
        SendMessageBatchRequestEntry entry = new SendMessageBatchRequestEntry();
        entry.setMessageBody("body");
        entry.setId("id");
        sendMessageBatchRequest.getEntries().add(entry);
        return getSqsClient().sendMessageBatch(sendMessageBatchRequest);
      }
    }.doWrappedOperation();
    assertThat(messageTotals(accountSQSClient, queueUrl) == 2, "There are 2 messages");
  }

  @Test
  public void testQueueUrlBindingReceiveMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingReceiveMessage");
    String queueName = "queue_name_test_binding_receive_message";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    // send a message
    String messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();

    // We should be done sooner, but give it two minutes
    boolean receivedMessage = false;
    long startTime = System.currentTimeMillis();
    while (!receivedMessage && System.currentTimeMillis() - startTime < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            receivedMessage = true;
            break;
          }
        }
      }
      Thread.sleep(1000L);
    }
    assertThat(receivedMessage, "Received the message sent");
    accountSQSClient.deleteQueue(queueUrl);
    accountSQSClient.createQueue(queueName);
    // send a message
    messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();

    // We should be done sooner, but give it two minutes
    receivedMessage = false;
    startTime = System.currentTimeMillis();
    while (!receivedMessage && System.currentTimeMillis() - startTime < 120000L) {
      ReceiveMessageResult receiveMessageResult = (ReceiveMessageResult)
        new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
          @Override
          public Object doOperation() {
            return getSqsClient().receiveMessage(new ReceiveMessageRequest());
          }
        }.doWrappedOperation();
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            receivedMessage = true;
            break;
          }
        }
      }
      Thread.sleep(1000L);
    }
    assertThat(receivedMessage, "Received the message sent");
  }

  @Test
  public void testQueueUrlBindingChangeMessageVisibility() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingChangeMessageVisibility");
    String queueName = "queue_name_test_binding_change_message_visibility";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("VisibilityTimeout", "0"));
    // send a message
    String messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();

    // We should be done sooner, but give it two minutes
    Message receivedMessage = null;
    long startTime = System.currentTimeMillis();
    while (receivedMessage == null && System.currentTimeMillis() - startTime < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            receivedMessage = message;
            break;
          }
        }
      }
      Thread.sleep(1000L);
    }
    assertThat(receivedMessage != null, "Received the message sent");

    final String receiptHandle = receivedMessage.getReceiptHandle();

    assertThat(getNumMessages(accountSQSClient, queueUrl) == 1
      && getNumMessagesNotVisible(accountSQSClient, queueUrl) == 0, "Message should be 'visible'");

    ChangeMessageVisibilityRequest changeMessageVisibilityRequest = new ChangeMessageVisibilityRequest();
    changeMessageVisibilityRequest.setQueueUrl(queueUrl);
    changeMessageVisibilityRequest.setReceiptHandle(receiptHandle);
    changeMessageVisibilityRequest.setVisibilityTimeout(30);
    accountSQSClient.changeMessageVisibility(changeMessageVisibilityRequest);

    assertThat(getNumMessages(accountSQSClient, queueUrl) == 0
      && getNumMessagesNotVisible(accountSQSClient, queueUrl) == 1, "Message should be 'invisible'");

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
        ChangeMessageVisibilityRequest changeMessageVisibilityRequest = new ChangeMessageVisibilityRequest();
        changeMessageVisibilityRequest.setReceiptHandle(receiptHandle);
        changeMessageVisibilityRequest.setVisibilityTimeout(0);
        getSqsClient().changeMessageVisibility(changeMessageVisibilityRequest);
        return null;
      }
    }.doWrappedOperation();

    assertThat(getNumMessages(accountSQSClient, queueUrl) == 1
      && getNumMessagesNotVisible(accountSQSClient, queueUrl) == 0, "Message should be 'visible' again");
  }

  @Test
  public void testQueueUrlBindingChangeMessageVisibilityBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingChangeMessageVisibilityBatch");
    String queueName = "queue_name_test_binding_change_message_visibility_batch";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("VisibilityTimeout", "0"));
    // send a message
    String messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();

    // We should be done sooner, but give it two minutes
    Message receivedMessage = null;
    long startTime = System.currentTimeMillis();
    while (receivedMessage == null && System.currentTimeMillis() - startTime < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            receivedMessage = message;
            break;
          }
        }
      }
      Thread.sleep(1000L);
    }
    assertThat(receivedMessage != null, "Received the message sent");

    final String receiptHandle = receivedMessage.getReceiptHandle();

    assertThat(getNumMessages(accountSQSClient, queueUrl) == 1
      && getNumMessagesNotVisible(accountSQSClient, queueUrl) == 0, "Message should be 'visible'");

    ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
    changeMessageVisibilityBatchRequest.setQueueUrl(queueUrl);
    ChangeMessageVisibilityBatchRequestEntry entry = new ChangeMessageVisibilityBatchRequestEntry();
    entry.setReceiptHandle(receiptHandle);
    entry.setId("id");
    entry.setVisibilityTimeout(30);
    changeMessageVisibilityBatchRequest.getEntries().add(entry);
    accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);

    assertThat(getNumMessages(accountSQSClient, queueUrl) == 0
      && getNumMessagesNotVisible(accountSQSClient, queueUrl) == 1, "Message should be 'invisible'");

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
        ChangeMessageVisibilityBatchRequest changeMessageVisibilityBatchRequest = new ChangeMessageVisibilityBatchRequest();
        ChangeMessageVisibilityBatchRequestEntry entry = new ChangeMessageVisibilityBatchRequestEntry();
        entry.setReceiptHandle(receiptHandle);
        entry.setId("id");
        entry.setVisibilityTimeout(0);
        changeMessageVisibilityBatchRequest.getEntries().add(entry);
        accountSQSClient.changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
        return getSqsClient().changeMessageVisibilityBatch(changeMessageVisibilityBatchRequest);
      }
    }.doWrappedOperation();

    assertThat(messageTotals(accountSQSClient, queueUrl) == 1
      && getNumMessagesNotVisible(accountSQSClient, queueUrl) == 0, "Message should be 'visible' again");
  }

  @Test
  public void testQueueUrlBindingDeleteMessage() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingDeleteMessage");
    String queueName = "queue_name_test_binding_delete_message";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    // send a message
    String messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();

    // We should be done sooner, but give it two minutes
    Message receivedMessage = null;
    long startTime = System.currentTimeMillis();
    while (receivedMessage == null && System.currentTimeMillis() - startTime < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            receivedMessage = message;
            break;
          }
        }
      }
      Thread.sleep(1000L);
    }
    assertThat(receivedMessage != null, "Received the message sent");

    final String receiptHandle1 = receivedMessage.getReceiptHandle();
    assertThat(messageTotals(accountSQSClient, queueUrl) == 1, "Should be 1 message");

    DeleteMessageRequest deleteMessageRequest = new DeleteMessageRequest();
    deleteMessageRequest.setQueueUrl(queueUrl);
    deleteMessageRequest.setReceiptHandle(receiptHandle1);
    accountSQSClient.deleteMessage(deleteMessageRequest);

    assertThat(messageTotals(accountSQSClient, queueUrl) == 0, "Should be 0 messages");

    messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();

    // We should be done sooner, but give it two minutes
    receivedMessage = null;
    startTime = System.currentTimeMillis();
    while (receivedMessage == null && System.currentTimeMillis() - startTime < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            receivedMessage = message;
            break;
          }
        }
      }
      Thread.sleep(1000L);
    }
    assertThat(receivedMessage != null, "Received the message sent");

    final String receiptHandle2 = receivedMessage.getReceiptHandle();
    assertThat(messageTotals(accountSQSClient, queueUrl) == 1, "Should be 1 message");

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
        DeleteMessageRequest deleteMessageRequest = new DeleteMessageRequest();
        deleteMessageRequest.setReceiptHandle(receiptHandle2);
        getSqsClient().deleteMessage(deleteMessageRequest);
        return null;
      }
    }.doWrappedOperation();

    assertThat(messageTotals(accountSQSClient, queueUrl) == 0, "Should be 0 messages");
  }

  @Test
  public void testQueueUrlBindingListDeadLetterSourceQueues() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingListDeadLetterSourceQueues");
    String queueName = "queue_name_test_binding_dead_letter_source";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    String queueArn = accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All")).getAttributes().get("QueueArn");
    int NUM_QUEUES = 5;
    // setup NUM_QUEUES queues (why not?)
    for (int i=0;i<NUM_QUEUES;i++) {
      String localQueueUrl = accountSQSClient.createQueue("queue_name_test_binding_dead_letter_dest_" + i).getQueueUrl();
      accountSQSClient.setQueueAttributes(localQueueUrl,
        ImmutableMap.of("RedrivePolicy", "{\"maxReceiveCount\":\"5\",\"deadLetterTargetArn\":\""+queueArn+"\"}"));
    }

    ListDeadLetterSourceQueuesRequest listDeadLetterSourceQueuesRequest = new ListDeadLetterSourceQueuesRequest();
    listDeadLetterSourceQueuesRequest.setQueueUrl(queueUrl);
    int numSources = accountSQSClient.listDeadLetterSourceQueues(listDeadLetterSourceQueuesRequest).getQueueUrls().size();
    assertThat(numSources == NUM_QUEUES, "Should have "+NUM_QUEUES+" queues");

    numSources =
      ((ListDeadLetterSourceQueuesResult) new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
        ListDeadLetterSourceQueuesRequest listDeadLetterSourceQueuesRequest = new ListDeadLetterSourceQueuesRequest();
        return getSqsClient().listDeadLetterSourceQueues(listDeadLetterSourceQueuesRequest);
      }
    }.doWrappedOperation()).getQueueUrls().size();
    assertThat(numSources == NUM_QUEUES, "Should have "+NUM_QUEUES+" queues");
  }

  @Test
  public void testQueueUrlBindingPurgeQueue() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingPurgeQueue");
    String queueName = "queue_name_test_binding_purge_queue";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    accountSQSClient.createQueue(queueName).getQueueUrl();

    int NUM_MESSAGES = 5;
    // setup NUM_QUEUES queues (why not?)
    for (int i=0;i<NUM_MESSAGES;i++) {
      accountSQSClient.sendMessage(queueUrl, "hello");
    }
    assertThat(messageTotals(accountSQSClient, queueUrl) == NUM_MESSAGES, "Should have "+NUM_MESSAGES+" messages");

    PurgeQueueRequest purgeQueueRequest = new PurgeQueueRequest();
    purgeQueueRequest.setQueueUrl(queueUrl);
    accountSQSClient.purgeQueue(purgeQueueRequest);
    assertThat(messageTotals(accountSQSClient, queueUrl) == 0, "Should have 0 messages");

    for (int i=0;i<NUM_MESSAGES;i++) {
      accountSQSClient.sendMessage(queueUrl, "hello");
    }
    assertThat(messageTotals(accountSQSClient, queueUrl) == NUM_MESSAGES, "Should have " + NUM_MESSAGES + " messages");

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
       getSqsClient().purgeQueue(new PurgeQueueRequest());
        return null;
      }
    }.doWrappedOperation();
    assertThat(messageTotals(accountSQSClient, queueUrl) == 0, "Should have 0 messages");
  }

  @Test
  public void testQueueUrlBindingDeleteMessageBatch() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlBindingDeleteMessageBatch");
    String queueName = "queue_name_test_binding_delete_message_batch";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    // send a message
    String messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();

    // We should be done sooner, but give it two minutes
    Message receivedMessage = null;
    long startTime = System.currentTimeMillis();
    while (receivedMessage == null && System.currentTimeMillis() - startTime < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            receivedMessage = message;
            break;
          }
        }
      }
      Thread.sleep(1000L);
    }
    assertThat(receivedMessage != null, "Received the message sent");

    final String receiptHandle1 = receivedMessage.getReceiptHandle();
    assertThat(messageTotals(accountSQSClient, queueUrl) == 1, "Should be 1 message");

    DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
    deleteMessageBatchRequest.setQueueUrl(queueUrl);
    DeleteMessageBatchRequestEntry entry = new DeleteMessageBatchRequestEntry();
    entry.setReceiptHandle(receiptHandle1);
    entry.setId("id");
    deleteMessageBatchRequest.getEntries().add(entry);
    accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);

    assertThat(messageTotals(accountSQSClient, queueUrl) == 0, "Should be 0 messages");

    messageId = accountSQSClient.sendMessage(queueUrl, "hello").getMessageId();

    // We should be done sooner, but give it two minutes
    receivedMessage = null;
    startTime = System.currentTimeMillis();
    while (receivedMessage == null && System.currentTimeMillis() - startTime < 120000L) {
      ReceiveMessageResult receiveMessageResult = accountSQSClient.receiveMessage(queueUrl);
      if (receiveMessageResult != null && receiveMessageResult.getMessages() != null) {
        for (Message message: receiveMessageResult.getMessages()) {
          if (message.getMessageId().equals(messageId)) {
            receivedMessage = message;
            break;
          }
        }
      }
      Thread.sleep(1000L);
    }
    assertThat(receivedMessage != null, "Received the message sent");

    final String receiptHandle2 = receivedMessage.getReceiptHandle();
    assertThat(messageTotals(accountSQSClient, queueUrl) == 1, "Should be 1 message");

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
        DeleteMessageBatchRequest deleteMessageBatchRequest = new DeleteMessageBatchRequest();
        DeleteMessageBatchRequestEntry entry = new DeleteMessageBatchRequestEntry();
        entry.setReceiptHandle(receiptHandle2);
        entry.setId("id");
        deleteMessageBatchRequest.getEntries().add(entry);
        accountSQSClient.deleteMessageBatch(deleteMessageBatchRequest);
        return getSqsClient().deleteMessageBatch(deleteMessageBatchRequest);
      }
    }.doWrappedOperation();

    assertThat(messageTotals(accountSQSClient, queueUrl) == 0, "Should be 0 messages");
  }

  @Test
  public void testQueueUrlGetAttribute() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlGetAttribute");
    String queueName = "queue_name_test_binding_get_attribute";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    accountSQSClient.setQueueAttributes(queueUrl, ImmutableMap.of("VisibilityTimeout", "60"));

    assertThat("60".equals(
      ((GetQueueAttributesResult) new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
        @Override
        public Object doOperation() {
          GetQueueAttributesRequest getQueueAttributesRequest = new GetQueueAttributesRequest();
          getQueueAttributesRequest.getAttributeNames().add("All");
          return getSqsClient().getQueueAttributes(getQueueAttributesRequest);
        }
      }.doWrappedOperation()).getAttributes().get("VisibilityTimeout")
    ), "Should match");
  }

  @Test
  public void testQueueUrlSetAttribute() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlSetAttribute");
    String queueName = "queue_name_test_binding_set_attribute";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl) {
      @Override
      public Object doOperation() {
        SetQueueAttributesRequest setQueueAttributesRequest = new SetQueueAttributesRequest();
        setQueueAttributesRequest.getAttributes().put("VisibilityTimeout", "60");
        getSqsClient().setQueueAttributes(setQueueAttributesRequest);
        return null;
      }
    }.doWrappedOperation();

    assertThat("60".equals(
      accountSQSClient.getQueueAttributes(queueUrl, Collections.singletonList("All")).getAttributes().get("VisibilityTimeout")
    ), "Should match");
  }

  @Test
  public void testQueueUrlRequestParameterWins() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlRequestParameterWins");
    String queueName1 = "queue_name_test_binding_request_parameter_wins_1";
    String queueUrl1 = accountSQSClient.createQueue(queueName1).getQueueUrl();
    String queueName2 = "queue_name_test_binding_request_parameter_wins_2";
    String queueUrl2 = accountSQSClient.createQueue(queueName2).getQueueUrl();

    new QueueOperationWithRequestParameter(accountSQSClient, queueUrl1) {
      @Override
      public Object doOperation() {
        // does not set as a request parameter, sets endpoint
        getSqsClient().deleteQueue(queueUrl2);
        return null;
      }
    }.doWrappedOperation();

    ListQueuesResult listQueuesResult = accountSQSClient.listQueues("queue_name_test_binding_request_parameter_wins");
    assertThat(listQueuesResult.getQueueUrls().size() == 1 && queueUrl2.equals(listQueuesResult.getQueueUrls().get(0)),
      "Should keep queue 2");
  }

  @Test
  public void testQueueUrlNot500Error() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueUrlNot500Error");
    String queueName = "queue_name_queue_url_not_500_error";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();

    // set the url without the path, had been an index out of bounds error (Internal error) before.
    // should just be a 404 error
    String queueUrlWithoutPath = queueUrl.substring(0,
      queueUrl.indexOf(new URL(queueUrl).getPath()));

    try {
      accountSQSClient.deleteQueue(queueUrlWithoutPath);
      assertThat(false, "Should not succeed in deleting queue with queue url " + queueUrlWithoutPath);
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 404, "Successfully did not delete queue with queue url " + queueUrlWithoutPath);
    }
  }



  private int getNumMessagesNotVisible(AmazonSQS sqsClient, String queueUrl) {
    Map<String, String> attributes =  sqsClient.getQueueAttributes(queueUrl, Collections.singletonList("All")).getAttributes();
    return Integer.parseInt(attributes.get("ApproximateNumberOfMessagesNotVisible"));
  }

  private int getNumMessages(AmazonSQS sqsClient, String queueUrl) {
    Map<String, String> attributes =  sqsClient.getQueueAttributes(queueUrl, Collections.singletonList("All")).getAttributes();
    return Integer.parseInt(attributes.get("ApproximateNumberOfMessages"));
  }

  private int messageTotals(AmazonSQS sqsClient, String queueUrl) {
    Map<String, String> attributes =  sqsClient.getQueueAttributes(queueUrl, Collections.singletonList("All")).getAttributes();
    return Integer.parseInt(attributes.get("ApproximateNumberOfMessages")) +
      Integer.parseInt(attributes.get("ApproximateNumberOfMessagesNotVisible")) +
      Integer.parseInt(attributes.get("ApproximateNumberOfMessagesDelayed"));
  }

  public abstract class QueueOperationWithRequestParameter {
    private AmazonSQS sqsClient;
    private String queueUrl;

    public AmazonSQS getSqsClient() {
      return sqsClient;
    }

    public String getQueueUrl() {
      return queueUrl;
    }

    public QueueOperationWithRequestParameter(AmazonSQS sqsClient, String queueUrl) {
      this.sqsClient = sqsClient;
      this.queueUrl = queueUrl;
    }

    public abstract Object doOperation();

    public Object doWrappedOperation() throws AmazonServiceException {
      RequestHandler2 addQueueUrl = new RequestHandler2() {
        public void beforeRequest(final Request<?> request) {
          request.addParameter("QueueUrl", queueUrl);
        }
      };
      ((AmazonWebServiceClient) sqsClient).addRequestHandler(addQueueUrl);
      try {
        return doOperation();
      } finally {
        ((AmazonWebServiceClient) sqsClient).removeRequestHandler(addQueueUrl);
      }
    }
  }
}
