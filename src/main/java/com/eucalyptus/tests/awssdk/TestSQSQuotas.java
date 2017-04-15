package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.github.sjones4.youcan.youare.model.PutAccountPolicyRequest;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;
import static com.eucalyptus.tests.awssdk.N4j.youAre;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSQuotas {

  private String account;
  private AmazonSQS accountSQSClient;

  @BeforeClass
  public void init() throws Exception {
    print("### PRE SUITE SETUP - " + this.getClass().getSimpleName());

    try {
      getCloudInfoAndSqs();
      account = "sqs-account-a-" + System.currentTimeMillis();
      SQSUtils.synchronizedCreateAccount(account);
      accountSQSClient = getSqsClientWithNewAccount(account, "admin");
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
  }

  @Test
  public void testQueueQuota() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testQueueQuota");
    PutAccountPolicyRequest putAccountPolicyRequest = new PutAccountPolicyRequest();
    putAccountPolicyRequest.setAccountName(account);
    putAccountPolicyRequest.setPolicyName("queuequota");
    putAccountPolicyRequest.setPolicyDocument(
      "{\n" +
        "  \"Version\":\"2011-04-01\",\n" +
        "  \"Statement\":[{\n" +
        "  \"Sid\":\"4\",\n" +
        "  \"Effect\":\"Limit\",\n" +
        "  \"Action\":\"sqs:CreateQueue\",\n" +
        "   \"Resource\":\"*\",\n" +
        "   \"Condition\":{\n" +
        "     \"NumericLessThanEquals\": { \"sqs:quota-queuenumber\":\"5\" }\n" +
        "   }\n" +
        "   }]\n" +
        "}\n"
    );
    youAre.putAccountPolicy(putAccountPolicyRequest);
    for (int i = 0; i < 5; i++) {
      accountSQSClient.createQueue("queue-" + i);
    }
    try {
      accountSQSClient.createQueue("queue-6");
      assertThat(false, "Should not succeed in creating queue that exceeds quota");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 400, "Successfully did not succeed in creating queue that exceeds quota");
    }
  }
}
