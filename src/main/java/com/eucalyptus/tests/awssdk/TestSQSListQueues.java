package com.eucalyptus.tests.awssdk;

import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.ListQueuesResult;
import com.beust.jcommander.internal.Sets;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Map;

import static com.eucalyptus.tests.awssdk.N4j.assertThat;
import static com.eucalyptus.tests.awssdk.N4j.getCloudInfoAndSqs;
import static com.eucalyptus.tests.awssdk.N4j.getSqsClientWithNewAccount;
import static com.eucalyptus.tests.awssdk.N4j.print;
import static com.eucalyptus.tests.awssdk.N4j.testInfo;

/**
 * Created by ethomas on 9/21/16.
 */
public class TestSQSListQueues {

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
  public void testListQueues() throws Exception {
    testInfo(this.getClass().getSimpleName() + " - testListQueues");
    Collection<String> queueNames = ImmutableSet.of(
      "a","b","c","aa","bb","cc","ab","ac","aab","aba","abb","abc"
    );
    Map<String, String> queueUrls = Maps.newHashMap();
    for (String queueName: queueNames) {
      queueUrls.put(queueName, accountSQSClient.createQueue(queueName).getQueueUrl());
    }

    assertThat(
      match(accountSQSClient.listQueues(), queueUrls,
        "a","b","c","aa","bb","cc","ab","ac","aab","aba","abb","abc"),
      "test no prefix");
    assertThat(
      match(accountSQSClient.listQueues("a"), queueUrls,
        "a","aa","ab","ac","aab","aba","abb","abc"),
      "test 'a' prefix");
    assertThat(
      match(accountSQSClient.listQueues("b"), queueUrls,
        "b","bb"),
      "test 'b' prefix");
    assertThat(
      match(accountSQSClient.listQueues("c"), queueUrls,
        "c","cc"),
      "test 'c' prefix");
    assertThat(
      match(accountSQSClient.listQueues("d"), queueUrls),
      "test 'd' prefix");
    assertThat(
      match(accountSQSClient.listQueues("aa"), queueUrls,
        "aa","aab"),
      "test 'aa' prefix");
    assertThat(
      match(accountSQSClient.listQueues("ab"), queueUrls,
        "ab","aba","abb","abc"),
      "test 'ab' prefix");
    assertThat(
      match(accountSQSClient.listQueues("ac"), queueUrls,
        "ac"),
      "test 'ac' prefix");
    assertThat(
      match(accountSQSClient.listQueues("aab"), queueUrls,
        "aab"),
      "test no prefix");
  }

  private boolean match(ListQueuesResult listQueueResult, Map<String, String> queueUrls, String... keys) {
    Collection<String> returnedQueueUrls = Sets.newHashSet();
    Collection<String> expectedQueueUrls = Sets.newHashSet();
    if (listQueueResult != null && listQueueResult.getQueueUrls() != null) {
      returnedQueueUrls.addAll(listQueueResult.getQueueUrls());
    }
    if (keys != null) {
      for (String key: keys) {
        expectedQueueUrls.add(queueUrls.get(key));
      }
    }
    return expectedQueueUrls.equals(returnedQueueUrls);
  }


}
