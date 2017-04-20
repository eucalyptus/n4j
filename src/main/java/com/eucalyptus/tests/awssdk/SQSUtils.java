package com.eucalyptus.tests.awssdk;

import static com.eucalyptus.tests.awssdk.N4j.*;
/**
 * Created by ethomas on 4/14/17.
 */
public class SQSUtils {

  public static synchronized void synchronizedCreateAccount(String account) {
    createAccount(account);
  }

  public static synchronized void synchronizedDeleteAccount(String account) {
    deleteAccount(account);
  }

}
