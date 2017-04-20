package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.beust.jcommander.internal.Sets;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Set;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.createUser;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.sqs;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 10/4/16.
 */
public class TestSQSAdminFunctions {


  private String account;
  private String otherAccount;
  private AmazonSQS accountSQSClient;
  private AmazonSQS otherAccountSQSClient;
  private AmazonSQS otherAccountUserSQSClient;
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
      createUser(otherAccount, "user");
      otherAccountUserSQSClient = getSqsClientWithNewAccount(otherAccount, "user");
    } catch (Exception e) {
      try {
        teardown();
      } catch (Exception ie) {
      }
      throw e;
    }
  }

  @Test
  public void testAdminFunctions() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testAdminFunctions");
    String queueName = "queue_name_admin_functions";
    String queueUrl = accountSQSClient.createQueue(queueName).getQueueUrl();
    // see if the admin can see this queue by listing queues verbose
    Set<String> allQueueUrls = Sets.newHashSet();
    allQueueUrls.addAll(sqs.listQueues("verbose").getQueueUrls());
    assertThat(allQueueUrls.contains(queueUrl), "Admin list queues (verbose) should see the other queue url");
    try {
      otherAccountSQSClient.deleteQueue(queueUrl);
      assertThat(false, "Should fail deleting queue on different account");
    } catch (AmazonServiceException e) {
      assertThat(e.getStatusCode() == 403, "Correctly fail deleting queue on different account");
    }
    sqs.deleteQueue(queueUrl);
    // should work
    allQueueUrls.clear();
    allQueueUrls.addAll(sqs.listQueues("verbose").getQueueUrls());
    assertThat(!allQueueUrls.contains(queueUrl), "Admin list queues (verbose) should no longer see the other queue url");
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


}