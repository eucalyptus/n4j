package com.eucalyptus.tests.awssdk;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testng.internal.collections.Pair;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 4/14/17.
 */
public class ConciseTestSQS {

  ExecutorService executorService = Executors.newFixedThreadPool(4);

  @BeforeClass
  public void init() throws Exception {
    executorService = Executors.newFixedThreadPool(4);
  }

  @AfterClass
  public void teardown() throws Exception {
    executorService.shutdown();
  }

  public abstract class CallableWithExceptions implements Callable<Pair<Optional<Exception>, Optional<AssertionError>>>, Comparable<CallableWithExceptions> {
    public abstract void doCall() throws Exception;

    public abstract long getPriority(); // value is approximate time in seconds of last test run.  We want the long ones to run first.

    @Override
    public final Pair<Optional<Exception>, Optional<AssertionError>> call() throws Exception {
      Exception exception = null;
      AssertionError assertionError = null;
      try {
        doCall();
      } catch (Exception e1) {
        exception = e1;
      } catch (AssertionError e2) {
        assertionError = e2;
      }
      return Pair.create(Optional.fromNullable(exception), Optional.fromNullable(assertionError));
    }

    @Override
    public int compareTo(CallableWithExceptions other) {
      return (int) (-1 * (getPriority() - other.getPriority()));
    }
  }

  @Test
  public void testAllConcise() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAllConcise");
    List<CallableWithExceptions> callables = Lists.newArrayList(
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSAdminFunctions testSQSAdminFunctions = new TestSQSAdminFunctions();
          try {
            testSQSAdminFunctions.init();
            testSQSAdminFunctions.testAdminFunctions();
          } finally {
            testSQSAdminFunctions.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 8L;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSAttributes testSQSAttributes = new TestSQSAttributes();
          try {
            testSQSAttributes.init();
            testSQSAttributes.testSetQueueAttributesOtherAccount();
            testSQSAttributes.testSetQueueAttributesNonExistentAccount();
            testSQSAttributes.testSetAttributesNonExistentQueue();
            testSQSAttributes.testSetReadOnlyQueueAttributes();
            testSQSAttributes.testSetBogusQueueAttributes();
            testSQSAttributes.testGetQueueAttributesOtherAccount();
            testSQSAttributes.testGetQueueAttributesNonExistentAccount();
            testSQSAttributes.testGetAttributesNonExistentQueue();
            testSQSAttributes.testGetBogusQueueAttributes();
            testSQSAttributes.testGetAttributesOnCreateQueue();
            testSQSAttributes.testGetAttributesOnSetAttributesQueue();
            testSQSAttributes.testChangeSomeAttributes();
            testSQSAttributes.testGetAttributesFilter();
          } finally {
            testSQSAttributes.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 11L;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSAttributeValuesInMessages testSQSAttributeValuesInMessages = new TestSQSAttributeValuesInMessages();
          try {
            testSQSAttributeValuesInMessages.init();
            testSQSAttributeValuesInMessages.testAttributeValuesInMessages();
          } finally {
            testSQSAttributeValuesInMessages.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 69L;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSChangeMessageVisibilityBatch testSQSChangeMessageVisibilityBatch = new TestSQSChangeMessageVisibilityBatch();
          try {
            testSQSChangeMessageVisibilityBatch.init();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchSuccess();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchNonExistentAccount();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchOtherAccount();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchNonExistentQueue();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchBogusReceiptHandles();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchLowTimeout();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchHighTimeout();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchDeletedMessage();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchOldMessage();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchTooFewMessages();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchTooManyMessages();
            testSQSChangeMessageVisibilityBatch.testChangeMessageVisibilityBatchId();
          } finally {
            testSQSChangeMessageVisibilityBatch.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 163L;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSChangeMessageVisibility testSQSChangeMessageVisibility = new TestSQSChangeMessageVisibility();
          try {
            testSQSChangeMessageVisibility.init();
            testSQSChangeMessageVisibility.testChangeMessageVisibilityNonExistentAccount();
            testSQSChangeMessageVisibility.testChangeMessageVisibilityOtherAccount();
            testSQSChangeMessageVisibility.testChangeMessageVisibilityNonExistentQueue();
            testSQSChangeMessageVisibility.testChangeMessageVisibilityReceiptHandleOtherAccount();
            testSQSChangeMessageVisibility.testChangeMessageVisibilityBogusReceiptHandles();
            testSQSChangeMessageVisibility.testChangeMessageVisibilityLowTimeout();
            testSQSChangeMessageVisibility.testChangeMessageVisibilityHighTimeout();
            testSQSChangeMessageVisibility.testChangeMessageVisibilityDeletedMessage();
            testSQSChangeMessageVisibility.testChangeMessageVisibilityOldMessage();
          } finally {
            testSQSChangeMessageVisibility.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 9L;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSCloudWatchMetrics testSQSCloudWatchMetrics = new TestSQSCloudWatchMetrics();
          try {
            testSQSCloudWatchMetrics.init();
            testSQSCloudWatchMetrics.testCloudWatchMetrics();
          } finally {
            testSQSCloudWatchMetrics.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 6010;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSCreateQueue testSQSCreateQueue = new TestSQSCreateQueue();
          try {
            testSQSCreateQueue.init();
            testSQSCreateQueue.testQueueUrlSyntax();
            testSQSCreateQueue.testQueueName();
            testSQSCreateQueue.testDelaySeconds();
            testSQSCreateQueue.testMessageRetentionPeriod();
            testSQSCreateQueue.testMaximumMessageSize();
            testSQSCreateQueue.testReceiveMessageWaitTimeSeconds();
            testSQSCreateQueue.testVisibilityTimeout();
            testSQSCreateQueue.testRedrivePolicy();
            testSQSCreateQueue.testPolicy();
            testSQSCreateQueue.testBadAttributes();
            testSQSCreateQueue.testRedrivePolicyJSON();
            testSQSCreateQueue.testMakeArn();
            testSQSCreateQueue.testChangeArnField();
          } finally {
            testSQSCreateQueue.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 9;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSCrossAccountStackPolicies testSQSCrossAccountStackPolicies = new TestSQSCrossAccountStackPolicies();
          try {
            testSQSCrossAccountStackPolicies.init();
            testSQSCrossAccountStackPolicies.testChangeMessageVisibility();
            testSQSCrossAccountStackPolicies.testChangeMessageVisibilityBatch();
            testSQSCrossAccountStackPolicies.testDeleteMessage();
            testSQSCrossAccountStackPolicies.testDeleteMessageBatch();
            testSQSCrossAccountStackPolicies.testGetQueueAttributes();
            testSQSCrossAccountStackPolicies.testGetQueueUrl();
            testSQSCrossAccountStackPolicies.testListDeadLetterSourceQueues();
            testSQSCrossAccountStackPolicies.testPurgeQueue();
            testSQSCrossAccountStackPolicies.testReceiveMessage();
            testSQSCrossAccountStackPolicies.testSendMessage();
            testSQSCrossAccountStackPolicies.testSendMessageBatch();
          } finally {
            testSQSCrossAccountStackPolicies.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 118;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSDeadLetterQueue testSQSDeadLetterQueue = new TestSQSDeadLetterQueue();
          try {
            testSQSDeadLetterQueue.init();
            testSQSDeadLetterQueue.testDeadLetterQueueMoveMessage();
            testSQSDeadLetterQueue.testDeadLetterQueueMoveMessageTwice();
            testSQSDeadLetterQueue.testDeadLetterQueueMoveOnlyIfExists();
          } finally {
            testSQSDeadLetterQueue.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 63;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSDelaySeconds testSQSDelaySeconds = new TestSQSDelaySeconds();
          try {
            testSQSDelaySeconds.init();
            testSQSDelaySeconds.testMessageDelay();
          } finally {
            testSQSDelaySeconds.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 103;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSDeleteMessageBatch testSQSDeleteMessageBatch = new TestSQSDeleteMessageBatch();
          try {
            testSQSDeleteMessageBatch.init();
            testSQSDeleteMessageBatch.testDeleteMessageBatchNonExistentAccount();
            testSQSDeleteMessageBatch.testDeleteMessageBatchOtherAccount();
            testSQSDeleteMessageBatch.testDeleteMessageBatchNonExistentQueue();
            testSQSDeleteMessageBatch.testDeleteMessageBatchBogusReceiptHandles();
            testSQSDeleteMessageBatch.testDeleteSubsequentReceiptHandles();
            testSQSDeleteMessageBatch.testDeleteMessageBatchTooFewMessages();
            testSQSDeleteMessageBatch.testDeleteMessageBatchTooManyMessages();
            testSQSDeleteMessageBatch.testDeleteMessageBatchId();
            testSQSDeleteMessageBatch.testDeleteMessageBatchSuccess();
          } finally {
            testSQSDeleteMessageBatch.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 138;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSDeleteMessage testSQSDeleteMessage = new TestSQSDeleteMessage();
          try {
            testSQSDeleteMessage.init();
            testSQSDeleteMessage.testDeleteMessageNonExistentAccount();
            testSQSDeleteMessage.testDeleteMessageOtherAccount();
            testSQSDeleteMessage.testDeleteMessageNonExistentQueue();
            testSQSDeleteMessage.testDeleteMessageBogusReceiptHandles();
            testSQSDeleteMessage.testDeleteSubsequentReceiptHandles();
          } finally {
            testSQSDeleteMessage.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 13;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSDeleteQueue testSQSDeleteQueue = new TestSQSDeleteQueue();
          try {
            testSQSDeleteQueue.init();
            testSQSDeleteQueue.testDeleteQueue();
            testSQSDeleteQueue.testDeleteQueueOtherAccount();
            testSQSDeleteQueue.testDeleteQueueNonExistentAccount();
            testSQSDeleteQueue.testDeleteNonExistentQueue();
            testSQSDeleteQueue.testDeleteQueueWithMessages();
            testSQSDeleteQueue.testDeleteAlreadyDeletedQueue();
          } finally {
            testSQSDeleteQueue.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 7;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSGetQueueUrl testSQSGetQueueUrl = new TestSQSGetQueueUrl();
          try {
            testSQSGetQueueUrl.init();
            testSQSGetQueueUrl.testGetQueueUrl();
            testSQSGetQueueUrl.testGetQueueUrlOtherAccount();
            testSQSGetQueueUrl.testGetQueueUrlNonExistentQueue();
            testSQSGetQueueUrl.testGetQueueUrlNonExistentAccount();
          } finally {
            testSQSGetQueueUrl.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 6;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSIAMPolicies testSQSIAMPolicies = new TestSQSIAMPolicies();
          try {
            testSQSIAMPolicies.init();
            testSQSIAMPolicies.testDeleteQueue();
            testSQSIAMPolicies.testChangeMessageVisibility();
            testSQSIAMPolicies.testChangeMessageVisibilityBatch();
            testSQSIAMPolicies.testDeleteMessage();
            testSQSIAMPolicies.testDeleteMessageBatch();
            testSQSIAMPolicies.testGetQueueAttributes();
            testSQSIAMPolicies.testGetQueueUrl();
            testSQSIAMPolicies.testListDeadLetterSourceQueues();
            testSQSIAMPolicies.testPurgeQueue();
            testSQSIAMPolicies.testReceiveMessage();
            testSQSIAMPolicies.testSendMessage();
            testSQSIAMPolicies.testSendMessageBatch();
            testSQSIAMPolicies.testParseInterval();
            testSQSIAMPolicies.testCreateQueue();
            testSQSIAMPolicies.testAddPermission();
            testSQSIAMPolicies.testListQueues();
            testSQSIAMPolicies.testRemovePermission();
            testSQSIAMPolicies.testSetQueueAttributes();
          } finally {
            testSQSIAMPolicies.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 458;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSListDeadLetterSourceQueues testSQSListDeadLetterSourceQueues = new TestSQSListDeadLetterSourceQueues();
          try {
            testSQSListDeadLetterSourceQueues.init();
            testSQSListDeadLetterSourceQueues.testListDeadLetterSourceQueuesOtherAccount();
            testSQSListDeadLetterSourceQueues.testListDeadLetterSourceQueuesNonExistentAccount();
            testSQSListDeadLetterSourceQueues.testListDeadLetterSourceQueuesNonExistentQueue();
            testSQSListDeadLetterSourceQueues.testListDeadLetterSourceQueuesRandomSample();
          } finally {
            testSQSListDeadLetterSourceQueues.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 34;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSListQueues testSQSListQueues = new TestSQSListQueues();
          try {
            testSQSListQueues.init();
            testSQSListQueues.testListQueues();
          } finally {
            testSQSListQueues.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 7;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSLongPolling testSQSLongPolling = new TestSQSLongPolling();
          try {
            testSQSLongPolling.init();
            testSQSLongPolling.testPollingTimeoutNoMessagesQueue10();
            testSQSLongPolling.testPollingTimeoutNoMessagesReceiveMessage5();
            testSQSLongPolling.testPollingTimeoutNoMessagesReceiveMessage10();
            testSQSLongPolling.testPollingTimeoutNoMessagesReceiveMessage15();
            testSQSLongPolling.testPollingTimeoutNoMessagesReceiveMessage20();
            testSQSLongPolling.testPollingTimeoutSuccessNoDelay();
            testSQSLongPolling.testPollingTimeoutSuccessDelayQueue();
            testSQSLongPolling.testPollingTimeoutSuccessDelayMessage();
            testSQSLongPolling.testPollingTimeoutSuccessMultipleReceivers();
            testSQSLongPolling.testPollingTimeoutSuccessFailureMultipleReceivers();
          } finally {
            testSQSLongPolling.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 167;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSMessageExpirationPeriod testSQSMessageExpirationPeriod = new TestSQSMessageExpirationPeriod();
          try {
            testSQSMessageExpirationPeriod.init();
            testSQSMessageExpirationPeriod.testMessageExpirationPeriod();
          } finally {
            testSQSMessageExpirationPeriod.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 96;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSPermissions testSQSPermissions = new TestSQSPermissions();
          try {
            testSQSPermissions.init();
            testSQSPermissions.testAddPermissionNonExistentAccountQueueUrl();
            testSQSPermissions.testAddPermissionOtherAccountQueueUrl();
            testSQSPermissions.testAddPermissionNonExistentQueueUrl();
            testSQSPermissions.testAddPermissionBadLabel();
            testSQSPermissions.testAddPermissionActions();
            testSQSPermissions.testAddPermissionAccounts();
            testSQSPermissions.testAddPermissionsFromEmptyPolicy();
            testSQSPermissions.testAddPermissionsFromExistingOneStatementPolicy();
            testSQSPermissions.testAddPermissionsFromExistingTwoStatementPolicy();
            testSQSPermissions.testRemovePermissionNonExistentAccountQueueUrl();
            testSQSPermissions.testRemovePermissionOtherAccountQueueUrl();
            testSQSPermissions.testRemovePermissionNonExistentQueueUrl();
            testSQSPermissions.testRemovePermissionBadLabel();
            testSQSPermissions.testRemovePermissionsSuccessful();
          } finally {
            testSQSPermissions.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 13;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSPurgeQueue testSQSPurgeQueue = new TestSQSPurgeQueue();
          try {
            testSQSPurgeQueue.init();
            testSQSPurgeQueue.testPurgeQueue();
            testSQSPurgeQueue.testPurgeQueueOtherAccount();
            testSQSPurgeQueue.testPurgeQueueNonExistentAccount();
            testSQSPurgeQueue.testPurgeNonExistentQueue();
            testSQSPurgeQueue.testPurgeQueueWithMessages();
          } finally {
            testSQSPurgeQueue.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 23;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSQueuePolicy testSQSQueuePolicy = new TestSQSQueuePolicy();
          try {
            testSQSQueuePolicy.init();
            testSQSQueuePolicy.testSqsQueuePolicy();
          } finally {
//            testSQSQueuePolicy.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 17;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSQueueUrlBinding testSQSQueueUrlBinding = new TestSQSQueueUrlBinding();
          try {
            testSQSQueueUrlBinding.init();
            testSQSQueueUrlBinding.testQueueUrlBindingDeleteQueue();
            testSQSQueueUrlBinding.testQueueUrlBindingAddPermission();
            testSQSQueueUrlBinding.testQueueUrlBindingRemovePermission();
            testSQSQueueUrlBinding.testQueueUrlBindingSendMessage();
            testSQSQueueUrlBinding.testQueueUrlBindingSendMessageBatch();
            testSQSQueueUrlBinding.testQueueUrlBindingReceiveMessage();
            testSQSQueueUrlBinding.testQueueUrlBindingChangeMessageVisibility();
            testSQSQueueUrlBinding.testQueueUrlBindingChangeMessageVisibilityBatch();
            testSQSQueueUrlBinding.testQueueUrlBindingDeleteMessage();
            testSQSQueueUrlBinding.testQueueUrlBindingListDeadLetterSourceQueues();
            testSQSQueueUrlBinding.testQueueUrlBindingPurgeQueue();
            testSQSQueueUrlBinding.testQueueUrlBindingDeleteMessageBatch();
            testSQSQueueUrlBinding.testQueueUrlGetAttribute();
            testSQSQueueUrlBinding.testQueueUrlSetAttribute();
            testSQSQueueUrlBinding.testQueueUrlRequestParameterWins();
            testSQSQueueUrlBinding.testQueueUrlNot500Error();
          } finally {
            testSQSQueueUrlBinding.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 23;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSQuotas testSQSQuotas = new TestSQSQuotas();
          try {
            testSQSQuotas.init();
            testSQSQuotas.testQueueQuota();
          } finally {
            testSQSQuotas.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 6;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSReadOnlyAttributes testSQSReadOnlyAttributes = new TestSQSReadOnlyAttributes();
          try {
            testSQSReadOnlyAttributes.init();
            testSQSReadOnlyAttributes.testReadOnlyAttributes();
          } finally {
            testSQSReadOnlyAttributes.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 67;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSReceiveMessage testSQSReceiveMessage = new TestSQSReceiveMessage();
          try {
            testSQSReceiveMessage.init();
            testSQSReceiveMessage.testVisibilityTimeout();
            testSQSReceiveMessage.testReceiveMaxNumberOfMessages();
            testSQSReceiveMessage.testReceiveMessageNonExistentAccount();
            testSQSReceiveMessage.testReceiveMessageOtherAccount();
            testSQSReceiveMessage.testReceiveMessageNonExistentQueue();
            testSQSReceiveMessage.testWaitTimeSeconds();
            testSQSReceiveMessage.testMaxNumberOfMessages();
            testSQSReceiveMessage.testMessagePartsAndFilters();
          } finally {
            testSQSReceiveMessage.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 25;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSSenderId testSQSSenderId = new TestSQSSenderId();
          try {
            testSQSSenderId.init();
            testSQSSenderId.testSenderId();
          } finally {
            testSQSSenderId.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 9;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSSendMessageBatch testSQSSendMessageBatch = new TestSQSSendMessageBatch();
          try {
            testSQSSendMessageBatch.init();
            testSQSSendMessageBatch.testSendMessageBatchNonExistentAccount();
            testSQSSendMessageBatch.testSendMessageBatchOtherAccount();
            testSQSSendMessageBatch.testSendMessageBatchNonExistentQueue();
            testSQSSendMessageBatch.testSendMessageBatchDelaySeconds();
            testSQSSendMessageBatch.testSendMessageBatchAttributeName();
            testSQSSendMessageBatch.testSendMessageBatchAttributeValue();
            testSQSSendMessageBatch.testSendMessageBatchBody();
            testSQSSendMessageBatch.testSendMessageBatchTooLongMessage();
            testSQSSendMessageBatch.testSendMessageBatchSuccess();
            testSQSSendMessageBatch.testSendMessageBatchTooFewMessages();
            testSQSSendMessageBatch.testSendMessageBatchTooManyMessages();
            testSQSSendMessageBatch.testSendMessageBatchTooLongTotal();
            testSQSSendMessageBatch.testSendMessageBatchId();
            testSQSSendMessageBatch.testSendMessageBatchSuccessMultiple();
            testSQSSendMessageBatch.testMessagesMatch();
          } finally {
            testSQSSendMessageBatch.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 14;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSSendMessage testSQSSendMessage = new TestSQSSendMessage();
          try {
            testSQSSendMessage.init();
            testSQSSendMessage.testDelaySeconds();
            testSQSSendMessage.testMessagesMatch();
            testSQSSendMessage.testSendMessageNonExistentAccount();
            testSQSSendMessage.testSendMessageOtherAccount();
            testSQSSendMessage.testSendMessageNonExistentQueue();
            testSQSSendMessage.testAttributeName();
            testSQSSendMessage.testAttributeValue();
            testSQSSendMessage.testBody();
            testSQSSendMessage.testTooLongMessage();
            testSQSSendMessage.testSendMessageSuccess();
          } finally {
            testSQSSendMessage.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 14;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSStatusCodesForNonexistentQueues testSQSStatusCodesForNonexistentQueues = new TestSQSStatusCodesForNonexistentQueues();
          try {
            testSQSStatusCodesForNonexistentQueues.init();
            testSQSStatusCodesForNonexistentQueues.testDeleteQueue();
            testSQSStatusCodesForNonexistentQueues.testChangeMessageVisibility();
            testSQSStatusCodesForNonexistentQueues.testChangeMessageVisibilityBatch();
            testSQSStatusCodesForNonexistentQueues.testDeleteMessage();
            testSQSStatusCodesForNonexistentQueues.testDeleteMessageBatch();
            testSQSStatusCodesForNonexistentQueues.testGetQueueAttributes();
            testSQSStatusCodesForNonexistentQueues.testGetQueueUrl();
            testSQSStatusCodesForNonexistentQueues.testListDeadLetterSourceQueues();
            testSQSStatusCodesForNonexistentQueues.testPurgeQueue();
            testSQSStatusCodesForNonexistentQueues.testReceiveMessage();
            testSQSStatusCodesForNonexistentQueues.testSendMessage();
            testSQSStatusCodesForNonexistentQueues.testSendMessageBatch();
            testSQSStatusCodesForNonexistentQueues.testAddPermission();
            testSQSStatusCodesForNonexistentQueues.testRemovePermission();
            testSQSStatusCodesForNonexistentQueues.testSetQueueAttributes();
          } finally {
            testSQSStatusCodesForNonexistentQueues.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 11;
        }
      },
      new CallableWithExceptions() {
        public void doCall() throws Exception {
          TestSQSVisibilityTimeout testSQSVisibilityTimeout = new TestSQSVisibilityTimeout();
          try {
            testSQSVisibilityTimeout.init();
            testSQSVisibilityTimeout.testVisibilityTimeout();
          } finally {
            testSQSVisibilityTimeout.teardown();
          }
        }

        @Override
        public long getPriority() {
          return 151;
        }
      }
    );
    Collections.sort(callables);
    List<Future<Pair<Optional<Exception>, Optional<AssertionError>>>> futures = Lists.newArrayList();
    for (CallableWithExceptions callableWithExceptions : callables) {
      futures.add(executorService.submit(callableWithExceptions));
    }
    for (Future<Pair<Optional<Exception>, Optional<AssertionError>>> future : futures) {
      Pair<Optional<Exception>, Optional<AssertionError>> pair = future.get();
      if (pair.first().isPresent()) {
        throw pair.first().get();
      } else if (pair.second().isPresent()) {
        throw pair.second().get();
      }
    }
  }
}
