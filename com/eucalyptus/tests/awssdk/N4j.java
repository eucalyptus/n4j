package com.eucalyptus.tests.awssdk;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.Request;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.handlers.AbstractRequestHandler;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.autoscaling.model.*;
import com.amazonaws.services.autoscaling.model.BlockDeviceMapping;
import com.amazonaws.services.autoscaling.model.DeletePolicyRequest;
import com.amazonaws.services.autoscaling.model.InstanceMonitoring;
import com.amazonaws.services.autoscaling.model.Tag;
import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancing.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancing.model.*;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancing.model.DescribeLoadBalancersResult;
import com.amazonaws.services.identitymanagement.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClient;
import com.github.sjones4.youcan.youare.YouAre;
import com.github.sjones4.youcan.youare.YouAreClient;
import com.github.sjones4.youcan.youare.model.CreateAccountRequest;
import com.github.sjones4.youcan.youare.model.DeleteAccountRequest;
import com.jcraft.jsch.*;
import org.apache.log4j.Logger;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

class N4j {
    static String CLC_IP = System.getProperty("clcip");
    static String USER = System.getProperty("user");
    static String PASSWORD = System.getProperty("password");
    static String endpointFile = System.getProperty("endpoints");
    static String LOCAL_INI_FILE = System.getProperty("inifile", "euca-admin.ini");
    static String REMOTE_INI_FILE ="/root/.euca/euca-admin.ini";
    static String LOCAL_EUCTL_FILE = System.getProperty("euctlfile", "euctl.ini");
    static String REMOTE_EUCTL_FILE ="/root/euctl.ini";

    static Logger logger = Logger.getLogger(N4j.class.getCanonicalName());
    static String EC2_ENDPOINT = null;
    static String AS_ENDPOINT = null;
    static String ELB_ENDPOINT = null;
    static String IAM_ENDPOINT = null;
    static String CW_ENDPOINT = null;
    static String S3_ENDPOINT = null;
    static String SQS_ENDPOINT = null;
    static String TOKENS_ENDPOINT = null;
    static String SECRET_KEY = null;
    static String ACCESS_KEY = null;
    static String ACCOUNT_ID = null;
    static String NAME_PREFIX;
    static String endpoints;
    static AmazonAutoScaling as;
    static AmazonEC2 ec2;
    static AmazonElasticLoadBalancing elb;
    static AmazonCloudWatch cw;
    static AmazonS3 s3;
    static AmazonSQS sqs;
    static YouAre youAre;
    static String IMAGE_ID = null;
    static String KERNEL_ID = null;
    static String RAMDISK_ID = null;
    static String AVAILABILITY_ZONE = null;
    static String INSTANCE_TYPE = "m1.small";

    public static void getCloudInfo() throws Exception {
        getAdminCreds(CLC_IP, USER, PASSWORD);

        if (endpointFile != null) {
            endpoints = endpointFile;
        } else {
            endpoints = "endpoints.xml";
        }

        print("Getting cloud information from " + LOCAL_INI_FILE);
        EC2_ENDPOINT = getAttribute(LOCAL_INI_FILE, "ec2-url");
        AS_ENDPOINT = getAttribute(LOCAL_INI_FILE, "autoscaling-url");
        ELB_ENDPOINT = getAttribute(LOCAL_INI_FILE, "elasticloadbalancing-url");
        CW_ENDPOINT = getAttribute(LOCAL_INI_FILE, "monitoring-url");
        IAM_ENDPOINT = getAttribute(LOCAL_INI_FILE, "iam-url");
        S3_ENDPOINT = getAttribute(LOCAL_INI_FILE, "s3-url");
        TOKENS_ENDPOINT = getAttribute(LOCAL_INI_FILE, "sts-url");
        SECRET_KEY = getAttribute(LOCAL_INI_FILE, "secret-key");
        ACCESS_KEY = getAttribute(LOCAL_INI_FILE, "key-id");
        ACCOUNT_ID = getAttribute(LOCAL_INI_FILE,"account-id");

        print("Updating endpoints file");
        updateEndpoints(endpoints, EC2_ENDPOINT, S3_ENDPOINT);

        print("Getting cloud connections");
        as = getAutoScalingClient(ACCESS_KEY, SECRET_KEY, AS_ENDPOINT);
        ec2 = getEc2Client(ACCESS_KEY, SECRET_KEY, EC2_ENDPOINT);
        elb = getElbClient(ACCESS_KEY, SECRET_KEY, ELB_ENDPOINT);
        cw = getCwClient(ACCESS_KEY, SECRET_KEY, CW_ENDPOINT);
        s3 = getS3Client(ACCESS_KEY, SECRET_KEY, S3_ENDPOINT);
        youAre = getYouAreClient(ACCESS_KEY, SECRET_KEY, IAM_ENDPOINT);
        IMAGE_ID = findImage();

        if (!isHVM()) {
            KERNEL_ID = findKernel();
            RAMDISK_ID = findRamdisk();
        }

        AVAILABILITY_ZONE = findAvailablityZone();
        NAME_PREFIX = eucaUUID() + "-";
        print("Using resource prefix for test: " + NAME_PREFIX);
        print("Cloud Discovery Complete");
    }

    public static void getCloudInfoAndSqs() throws Exception {
        getCloudInfo();
        getConfigProperties(CLC_IP, USER, PASSWORD);
        print("Getting sqs info from " + LOCAL_INI_FILE);
        SQS_ENDPOINT = getAttribute(LOCAL_INI_FILE, "simplequeue-url");
        sqs = getSqsClient(ACCESS_KEY, SECRET_KEY, SQS_ENDPOINT);
    }

    public static AmazonSQS getSqsClientWithNewAccount(String account, String user) throws Exception {
        if (SQS_ENDPOINT == null) {
            throw new Exception("Please run getCloudInfoAndSQS() first");
        }
        AWSCredentials creds = getUserCreds(account, user);
      print("creds.getAWSAccessKeyId()="+creds.getAWSAccessKeyId());
      print("creds.getAWSSecretKey()="+creds.getAWSSecretKey());
      print("SQS_ENDPOINT="+SQS_ENDPOINT);
        return getSqsClient(creds.getAWSAccessKeyId(), creds.getAWSSecretKey(), SQS_ENDPOINT);
    }

    // Quick way to initialize just the S3 client without initializing other clients in getCloudInfo().
    // For ease of use against AWS (mainly) as well as Eucalyptus
    public static void initS3Client() throws Exception {
        getAdminCreds(CLC_IP, USER, PASSWORD);
        if (endpointFile != null) {
            endpoints = endpointFile;
        } else {
            endpoints = "endpoints.xml";
        }

        print("Getting cloud information from " + LOCAL_INI_FILE);

        EC2_ENDPOINT = getAttribute(LOCAL_INI_FILE, "ec2-url");
        S3_ENDPOINT = getAttribute(LOCAL_INI_FILE, "s3-url");

        SECRET_KEY = getAttribute(LOCAL_INI_FILE, "secret-key");
        ACCESS_KEY = getAttribute(LOCAL_INI_FILE, "key-id");

        print("Updating endpoints file");
        updateEndpoints(endpoints, EC2_ENDPOINT, S3_ENDPOINT);

        print("Initializing S3 connections");
        s3 = getS3Client(ACCESS_KEY, SECRET_KEY, S3_ENDPOINT);

        print("S3 Discovery Complete");
    }
    
	public static AmazonS3 initS3ClientWithNewAccount(String account, String user) throws Exception {

		// Initialize everything for the first time
		if (EC2_ENDPOINT == null || S3_ENDPOINT == null || IAM_ENDPOINT == null || ACCESS_KEY == null || SECRET_KEY == null) {
            getAdminCreds(CLC_IP, USER, PASSWORD);
			if (endpointFile != null) {
				endpoints = endpointFile;
			} else {
				endpoints = "endpoints.xml";
			}

			print("Getting cloud information from " + LOCAL_INI_FILE);

			EC2_ENDPOINT = getAttribute(LOCAL_INI_FILE, "ec2-url");
			S3_ENDPOINT = getAttribute(LOCAL_INI_FILE, "s3-url");
			IAM_ENDPOINT = getAttribute(LOCAL_INI_FILE, "iam-url");

            SECRET_KEY = getAttribute(LOCAL_INI_FILE, "secret-key");
            ACCESS_KEY = getAttribute(LOCAL_INI_FILE, "key-id");

			print("Updating endpoints file");
			updateEndpoints(endpoints, EC2_ENDPOINT, S3_ENDPOINT);

			youAre = getYouAreClient(ACCESS_KEY, SECRET_KEY, IAM_ENDPOINT);
		}

		// Create a new account if one does not exist
		try {
			createAccount(account);
			if (!user.equalsIgnoreCase("admin")) {
				createUser(account, user);
			}
		} catch (Exception e) {
			// Account may already exist, try getting the keys
		}
		Map<String, String> keyMap = getUserKeys(account, user);

		// Initialize the s3 client and return it
		return getS3Client(keyMap.get("ak"), keyMap.get("sk"), S3_ENDPOINT);
	}

    public static void minimalInit() throws Exception {
        getAdminCreds(CLC_IP, USER, PASSWORD);
        EC2_ENDPOINT = getAttribute(LOCAL_INI_FILE, "ec2-url");
        print("HOST = " + CLC_IP);
        SECRET_KEY = getAttribute(LOCAL_INI_FILE, "secret-key");
        ACCESS_KEY = getAttribute(LOCAL_INI_FILE, "key-id");
        print("Cloud Discovery Complete");
    }
    

    public static void testInfo(String testName) {
        print("*****TEST NAME: " + testName);
    }

    /*
    Here take the ini param and look for that file. if it is there get it
    If it is not there then we will try to create creds
     */
    public static void getAdminCreds(String clcip, String user, String password) {
        print("CLC IP: " + clcip);
        SftpATTRS attrs = null;

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, clcip, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            print("Establishing Connection...");
            session.connect();
            print("Connection established.");
            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            // check to see if there are already creds created by us present
            try {
                attrs = sftpChannel.stat(REMOTE_INI_FILE);
            } catch (Exception e){
                print("No existing test creds found.");
            }

            // if there are not creds already created by us create them
            if (attrs != null) {
                print("Existing test creds found");
            } else {
                print("Creating test creds: " + REMOTE_INI_FILE);
                String command = "eval `clcadmin-assume-system-credentials`; " +
                        "DNSDOMAIN=`euctl -n system.dns.dnsdomain`; " +
                        "euare-useraddkey admin -wd $DNSDOMAIN &> " + REMOTE_INI_FILE + ";" +
                        "echo [global] >> " + REMOTE_INI_FILE + ";" +
                        "echo default-region = $DNSDOMAIN >>  " + REMOTE_INI_FILE;
                Channel channel=session.openChannel("exec");
                ((ChannelExec)channel).setCommand(command);
                channel.connect();
                InputStream in=channel.getInputStream();
                byte[] tmp=new byte[1024];
                while(true){
                    while(in.available()>0){
                        int i=in.read(tmp, 0, 1024);
                        if(i<0)break;
                        print(new String(tmp, 0, i));
                    }
                    if(channel.isClosed()){
                        if(in.available()>0) continue;
                        print("Get creds exit-status: "+channel.getExitStatus());
                        break;
                    }
                    try{Thread.sleep(1000);}catch(Exception ee){}
                }
                channel.disconnect();
            }
            session.disconnect();
        }
        catch(JSchException | IOException e) {
            System.err.print(e);
        }
        // we have known creds exist or we have created them by now so save them locally for processing
        print("Fetching euca-admin.ini file");
        getRemoteFile(clcip, user, password, REMOTE_INI_FILE, "euca-admin.ini");
    }

    public static void getConfigProperties(String clcip, String user, String password) {
        print("CLC IP: " + clcip);

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, clcip, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            print("Establishing Connection...");
            session.connect();
            print("Connection established.");


            print("Creating config properties: " + REMOTE_EUCTL_FILE);
            String command = "eval `clcadmin-assume-system-credentials`; " +
                "euctl > " + REMOTE_EUCTL_FILE;
            Channel channel=session.openChannel("exec");
            ((ChannelExec)channel).setCommand(command);
            channel.connect();
            InputStream in=channel.getInputStream();
            byte[] tmp=new byte[1024];
            while(true){
                while(in.available()>0){
                    int i=in.read(tmp, 0, 1024);
                    if(i<0)break;
                    print(new String(tmp, 0, i));
                }
                if(channel.isClosed()){
                    if(in.available()>0) continue;
                    print("Creating config properties exit-status: "+channel.getExitStatus());
                    break;
                }
                try{Thread.sleep(1000);}catch(Exception ee){}
            }
            channel.disconnect();
            session.disconnect();
        }
        catch(JSchException | IOException e) {
            System.err.print(e);
        }
        print("Fetching " + REMOTE_EUCTL_FILE + " file");
        getRemoteFile(clcip, user, password, REMOTE_EUCTL_FILE, LOCAL_EUCTL_FILE);
    }


    public static void getRemoteFile(String clcip, String user, String password, String remoteFile, String localFile) {
        try
        {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, clcip, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();
            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();
            InputStream out;
            out = sftpChannel.get(remoteFile);
            BufferedReader reader = new BufferedReader(new InputStreamReader(out));
            BufferedWriter writer = new BufferedWriter(new FileWriter(new File(localFile)));
            String line;
            while ((line = reader.readLine()) != null)
            {
                writer.write(line);
                // must do this: .readLine() will have stripped line endings
                writer.newLine();
            }
            reader.close();
            writer.close();
            sftpChannel.disconnect();
            session.disconnect();
        }
        catch(JSchException | SftpException | IOException e)
        {
            System.out.print(e);
        }
    }

    public static void removeRemoteFile(String clcip, String user, String password, String remoteFile) {
        SftpATTRS attrs = null;

        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(user, clcip, 22);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            print("Establishing Connection...");
            session.connect();
            print("Connection established.");
            ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
            sftpChannel.connect();

            // check to see if there are already creds created by us present
            try {
                attrs = sftpChannel.stat(remoteFile);
            } catch (Exception e){
                print("No existing test creds found.");
            }

            // if there are creds already created by us delete them
            if (attrs == null) {
                print("Nothing to delete.");
            } else {
                print("Removing test creds: " + remoteFile);
                String command = "rm -rf " + remoteFile;
                Channel channel=session.openChannel("exec");
                ((ChannelExec)channel).setCommand(command);
                channel.connect();
                InputStream in=channel.getInputStream();
                byte[] tmp=new byte[1024];
                while(true){
                    while(in.available()>0){
                        int i=in.read(tmp, 0, 1024);
                        if(i<0)break;
                        print(new String(tmp, 0, i));
                    }
                    if(channel.isClosed()){
                        if(in.available()>0) continue;
                        print("Remove remote file exit-status: "+channel.getExitStatus());
                        break;
                    }
                    try{Thread.sleep(1000);}catch(Exception ee){}
                }
                channel.disconnect();
            }
            session.disconnect();
        }
        catch(JSchException | IOException e) {
            System.err.print(e);
        }
    }

    /**
     * create ec2 connection based with supplied accessKey and secretKey
     *
     * @param accessKey
     * @param secretKey
     */
    static AmazonEC2 getEc2Client(String accessKey, String secretKey,
                                  String endpoint) {
        AWSCredentials creds = new BasicAWSCredentials(accessKey, secretKey);
        final AmazonEC2 ec2 = new AmazonEC2Client(creds);
        ec2.setEndpoint(endpoint);
        return ec2;
    }

    public static AmazonAutoScaling getAutoScalingClient(String accessKey,
                                                         String secretKey, String endpoint) {
        AWSCredentials creds = new BasicAWSCredentials(accessKey, secretKey);
        final AmazonAutoScaling as = new AmazonAutoScalingClient(creds);
        as.setEndpoint(endpoint);
        return as;
    }

      private static AmazonSQS getSqsClient(String accessKey, String secretKey,
                                                         String endpoint) {
        AWSCredentials creds = new BasicAWSCredentials(accessKey, secretKey);
        final AmazonSQS sqs = new AmazonSQSClient(creds);
        sqs.setEndpoint(endpoint);
        return sqs;
    }

    private static AmazonElasticLoadBalancing getElbClient(String accessKey, String secretKey,
                                                           String endpoint) {
        AWSCredentials creds = new BasicAWSCredentials(accessKey, secretKey);
        final AmazonElasticLoadBalancing elb = new AmazonElasticLoadBalancingClient(creds);
        elb.setEndpoint(endpoint);
        return elb;
    }

    /**
     * The YouAre interface extends AmazonIdentityManagement so you have the
     * regular IAM actions plus (a few) Euare specific ones.
     */
    public static YouAre getYouAreClient(String accessKey, String secretKey,
                                                        String endpoint) {
        AWSCredentialsProvider awsCredentialsProvider =
                new StaticCredentialsProvider( new BasicAWSCredentials(accessKey, secretKey));
        final YouAre youAre = new YouAreClient(awsCredentialsProvider);
        youAre.setEndpoint(endpoint);
        return youAre;
    }

    public static AmazonCloudWatch getCwClient(String accessKey, String secretKey,
                                               String endpoint) {
        AWSCredentials creds = new BasicAWSCredentials(accessKey, secretKey);
        final AmazonCloudWatch cw = new AmazonCloudWatchClient(creds);
        cw.setEndpoint(endpoint);
        return cw;
    }

    public static AmazonS3 getS3Client(String accessKey, String secretKey,
                                       String endpoint) {
        return getS3Client(new BasicAWSCredentials(accessKey, secretKey), endpoint);
    }

    public static AmazonS3 getS3Client(AWSCredentials credentials, String endpoint) {
        final AmazonS3 s3 =
            new AmazonS3Client(credentials, new ClientConfiguration( ).withSignerOverride("S3SignerType"));
        s3.setEndpoint(endpoint);
        return s3;
    }

    public static String getConfigProperty(String configPath, String field) throws IOException {
        Charset charset = Charset.forName("UTF-8");
        String result = null;
        try {
            List<String> lines = Files.readAllLines(Paths.get(configPath), charset);
            for (String line : lines) {
                if (line.contains(field)) {
                    result = line.substring(line.indexOf('=') + 2);
                    break;
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return result;
    }


    /**
     * @param credpath
     * @param field
     * @return the value of the field from eucarc file
     * @throws IOException
     */
    public static String getAttribute(String credpath, String field) throws IOException {
        Charset charset = Charset.forName("UTF-8");
        String result = null;
        try {
            List<String> lines = Files.readAllLines(Paths.get(credpath), charset);
            for (String line : lines) {
                if (line.contains(field)) {
                    result = line.substring(line.lastIndexOf('=') + 2);
                    break;
                }
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return result;
    }

    public static void updateEndpoints(String endpoints, String ec2Endpoint, String s3Endpoint) throws IOException {
        Path path = Paths.get(endpoints);
        Charset charset = Charset.forName("UTF-8");
        String content = new String(Files.readAllBytes(path), charset);
        content = content.replaceAll("EC2_URL", ec2Endpoint);
        content = content.replaceAll("WALRUS_URL", s3Endpoint);
        Files.write(path, content.getBytes(charset));
    }

    public static String eucaUUID() {
        return Long.toHexString(Double.doubleToLongBits(Math.random()));
    }

    public static void assertThat(boolean condition, String message) {
        assert condition : message;
    }

    public static void print(String text) {
        logger.info(text);
    }

    /**
     * helper method to pause execution
     *
     * @param secs time to sleep in seconds
     */
    public static void sleep(int secs) {
        try {
            Thread.sleep(secs * 1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void verifyInstanceHealthStatus(final String instanceId, final String expectedStatus) {
        String healthStatus = getHealthStatus(instanceId);
        assertThat(expectedStatus.equals(healthStatus), "Expected " + expectedStatus + " health status");
    }

    public static void waitForHealthStatus(final String instanceId, final String expectedStatus)
            throws Exception {
        final long startTime = System.currentTimeMillis();
        final long timeout = TimeUnit.MINUTES.toMillis(15);
        boolean completed = false;
        while (!completed && (System.currentTimeMillis() - startTime) < timeout) {
            Thread.sleep(5000);
            final String healthStatus = getHealthStatus(instanceId);
            completed = expectedStatus.equals(healthStatus);
        }
        assertThat(completed, "Instances health status did not change to "
                + expectedStatus + " within the expected timeout");
        print("Instance health status changed in " + (System.currentTimeMillis() - startTime) + "ms");
    }

    public static String getHealthStatus(final String instanceId) {
        final DescribeAutoScalingInstancesResult instancesResult = as
                .describeAutoScalingInstances(new DescribeAutoScalingInstancesRequest().withInstanceIds(instanceId));
        assertThat(instancesResult.getAutoScalingInstances().size() == 1, "Auto scaling instance found");
        final AutoScalingInstanceDetails details = instancesResult.getAutoScalingInstances().get(0);
        final String healthStatus = details.getHealthStatus();
        print("Health status: " + healthStatus);
        return healthStatus;
    }

    public static List<?> waitForInstances(final long timeout, final int expectedCount, final String groupName,
                                           final boolean asString) throws Exception {
        final long startTime = System.currentTimeMillis();
        boolean completed = false;
        if (asString) {
            List<?> instanceIds = Collections.emptyList();
            while (!completed && (System.currentTimeMillis() - startTime) < timeout) {
                Thread.sleep(5000);
                instanceIds = getInstancesForGroup(groupName, "running", true);
                completed = instanceIds.size() == expectedCount;
            }
            assertThat(completed, "Instances count did not change to " + expectedCount + " within the expected timeout");
            print("Instance count changed in " + (System.currentTimeMillis() - startTime) + "ms");
            return instanceIds;
        } else {
            List<?> instances = Collections.emptyList();
            while (!completed && (System.currentTimeMillis() - startTime) < timeout) {
                Thread.sleep(5000);
                instances = getInstancesForGroup(groupName, "running", false);
                completed = instances.size() == expectedCount;
            }
            assertThat(completed, "Instances count did not change to " + expectedCount + " within the expected timeout");
            print("Instance count changed in " + (System.currentTimeMillis() - startTime) + "ms");
            return instances;
        }
    }

    public static List<?> getInstancesForGroup(final String groupName, final String status, final boolean asString) {
        final DescribeInstancesResult instancesResult = ec2
                .describeInstances(new DescribeInstancesRequest()
                        .withFilters(new Filter()
                                .withName("tag:aws:autoscaling:groupName")
                                .withValues(groupName)));
        if (asString) {
            final List<String> instanceIds = new ArrayList<String>();
            for (final Reservation reservation : instancesResult.getReservations()) {
                for (final Instance instance : reservation.getInstances()) {
                    if (status == null || instance.getState() == null
                            || status.equals(instance.getState().getName())) {
                        instanceIds.add(instance.getInstanceId());
                    }
                }
            }
            return instanceIds;
        } else {
            final List<Instance> instances = new ArrayList<Instance>();
            for (final Reservation reservation : instancesResult.getReservations()) {
                for (final Instance instance : reservation.getInstances()) {
                    if (status == null || instance.getState() == null
                            || status.equals(instance.getState().getName())) {
                        instances.add(instance);
                    }
                }
            }
            return instances;
        }
    }

    /**
     * Wait for instance steady state (no PENDING, no STOPPING, no SHUTTING-DOWN)
     */
    public static void waitForInstances(final long timeout) {
        final long startTime = System.currentTimeMillis();
        withWhile:
        while (true) {
            if ((System.currentTimeMillis() - startTime) > timeout) {
                throw new IllegalStateException("Instance wait timed out");
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            DescribeInstancesResult result = ec2.describeInstances();
            for (final Reservation reservation : result.getReservations()) {
                for (final Instance instance : reservation.getInstances()) {
                    switch (instance.getState().getCode()) {
                        case 0:
                        case 32:
                        case 64:
                            continue withWhile;
                    }
                }
            }
            break;
        }
    }

    /**
     * Wait for volume steady state (no creating, no deleting)
     */
    public static void waitForVolumes(final long timeout) {
        final long startTime = System.currentTimeMillis();
        withWhile:
        while (true) {
            if ((System.currentTimeMillis() - startTime) > timeout) {
                throw new IllegalStateException("Volume wait timed out");
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            final DescribeVolumesResult result = ec2.describeVolumes();
            for (final Volume volume : result.getVolumes()) {
                if ("creating".equals(volume.getState()) ||
                        "deleting".equals(volume.getState())) continue withWhile;
            }
            break;
        }
    }

    /**
     * Wait for snapshot steady state (no pending)
     */
    public static void waitForSnapshots(final long timeout) {
        final long startTime = System.currentTimeMillis();
        withWhile:
        while (true) {
            if ((System.currentTimeMillis() - startTime) > timeout) {
                throw new IllegalStateException("Snapshot wait timed out");
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            final DescribeSnapshotsResult result = ec2.describeSnapshots();
            for (final Snapshot snapshot : result.getSnapshots()) {
                if ("pending".equals(snapshot.getState())) continue withWhile;
            }
            break;
        }
    }

    public static String findImage() {
        // Find an appropriate image to launch: instance-store not windows and not load balancer or image worker images
        String imageId=null;
        final DescribeImagesResult imagesResult = ec2
                .describeImages(new DescribeImagesRequest().withFilters(
                        new Filter().withName("image-type").withValues("machine"),
                        new Filter().withName("root-device-type").withValues("instance-store"),
                        new Filter().withName("is-public").withValues("true"),
                        new Filter().withName("virtualization-type").withValues("hvm")));
        for (Image i : imagesResult.getImages()){
            if (!i.getImageLocation().equals("imaging-worker-v1/eucalyptus-imaging-worker-image.img.manifest.xml") &&
                    !i.getImageLocation().equals("loadbalancer-v1/eucalyptus-load-balancer-image.img.manifest.xml") &&
                    !i.getPlatform().equals("windows")) {
                imageId = i.getImageId();
            }
        }
        assertThat(imageId != null, "No suitable image found");
        print("Using image: " + imageId);
        return imageId;
    }

    public static boolean isHVM() {
        final DescribeImagesResult imagesResult = ec2
                .describeImages(new DescribeImagesRequest().withFilters(
                        new Filter().withName("image-id").withValues(
                                findImage()),
                        new Filter().withName("virtualization-type").withValues(
                                "hvm")));
        return (imagesResult.getImages().size() != 0);
    }

    public static String findKernel() {
        // Find an appropriate image to launch
        final DescribeImagesResult imagesResult = ec2
                .describeImages(new DescribeImagesRequest()
                        .withFilters(
                                new Filter().withName("kernel-id").withValues(
                                        "eki-*")));
        assertThat(imagesResult.getImages().size() > 0,
                "Kernel not found (image with explicit kernel and ramdisk required)");

        print("Using kernel: " + imagesResult.getImages().get(0).getKernelId());
        return imagesResult.getImages().get(0).getKernelId();
    }

    public static String findRamdisk() {
        // Find an appropriate image to launch
        final DescribeImagesResult imagesResult = ec2
                .describeImages(new DescribeImagesRequest()
                        .withFilters(
                                new Filter().withName("ramdisk-id").withValues(
                                        "eri-*")));
        assertThat(imagesResult.getImages().size() > 0,
                "RamDisk not found (image with explicit kernel and ramdisk required)");

        print("Using ramdisk: " + imagesResult.getImages().get(0).getRamdiskId());
        return imagesResult.getImages().get(0).getRamdiskId();
    }

    public static String findAvailablityZone() {
        // Find an AZ to use
        final DescribeAvailabilityZonesResult azResult = ec2
                .describeAvailabilityZones();

        assertThat(azResult.getAvailabilityZones().size() > 0,
                "Availability zone not found");

        final String availabilityZone = azResult.getAvailabilityZones().get(0)
                .getZoneName();
        print("Using availability zone: " + availabilityZone);
        return availabilityZone;
    }

    public static List<AvailabilityZone> getAZ() {
        // Find an AZ to use
        final DescribeAvailabilityZonesResult azResult = ec2
                .describeAvailabilityZones();

        assertThat(azResult.getAvailabilityZones().size() > 0,
                "Availability zone not found");

        return azResult.getAvailabilityZones();
    }

    /**
     * @param name
     * @param desc
     */
    public static void createSecurityGroup(String name, String desc) {
        try {
            CreateSecurityGroupRequest securityGroupRequest = new CreateSecurityGroupRequest(
                    name, desc);
            ec2.createSecurityGroup(securityGroupRequest);
            print("Created Security Group: " + name);
        } catch (AmazonServiceException ase) {
            // Likely this means that the group is already created, so ignore.
            print(ase.getMessage());
        }
    }

    /**
     * @return list of security groups
     */
    public static List<SecurityGroup> describeSecurityGroups() {
        DescribeSecurityGroupsResult securityGroupsResult = null;
        try {
            DescribeSecurityGroupsRequest describeSecurityGroupsRequest = new DescribeSecurityGroupsRequest();
            securityGroupsResult = ec2
                    .describeSecurityGroups(describeSecurityGroupsRequest);
        } catch (AmazonServiceException ase) {
            // Likely this means that the group is already created, so ignore.
            print(ase.getMessage());
        }
        return securityGroupsResult.getSecurityGroups();
    }

    /**
     * @param groupName security group to delete
     */
    public static void deleteSecurityGroup(String groupName) {
        try {
            DeleteSecurityGroupRequest deleteSecurityGroupRequest = new DeleteSecurityGroupRequest(
                    groupName);
            ec2.deleteSecurityGroup(deleteSecurityGroupRequest);
            print("Deleted Security Group: " + groupName);
        } catch (AmazonServiceException ase) {
            print(ase.getMessage());
        }
    }

    /**
     * @param emi
     * @param keyName
     * @param type
     * @param securityGroups
     */
    public static void runInstances(String emi, String keyName,
                                    String type, List<String> securityGroups, int minCount,
                                    int maxCount) {
        RunInstancesRequest runInstancesRequest = new RunInstancesRequest()
                .withInstanceType(type).withImageId(emi).withMinCount(minCount)
                .withMaxCount(maxCount).withSecurityGroups(securityGroups)
                .withKeyName(keyName);
        ec2.runInstances(runInstancesRequest);
        print("Started instance: "
                + getLastlaunchedInstance().get(0).getInstanceId());
    }

    /**
     * @param instanceIds
     */
    public static void stopInstances(List<String> instanceIds) {
        StopInstancesRequest stopInstancesRequest = new StopInstancesRequest(
                instanceIds);
        ec2.stopInstances(stopInstancesRequest);
        for (String instance : instanceIds) {
            print("Stopped instance: " + instance);
        }
    }

    /**
     * @param instanceIds
     */
    public static void startInstances(List<String> instanceIds) {
        StartInstancesRequest startInstancesRequest = new StartInstancesRequest(
                instanceIds);
        ec2.startInstances(startInstancesRequest);
        for (String instance : instanceIds) {
            print("Started instance: " + instance);
        }
    }

    /**
     * @param instanceIds
     */
    public static void terminateInstances(List<String> instanceIds) {
        TerminateInstancesRequest terminateInstancesRequest = new TerminateInstancesRequest(instanceIds);
        ec2.terminateInstances(terminateInstancesRequest);
        for (String instance : instanceIds) {
            print("Terminated instance: " + instance);
        }
    }

    /**
     * @return # of reservations
     */
    public static List<Reservation> getInstancesList() {
        DescribeInstancesResult describeInstancesRequest = ec2.describeInstances();
        return describeInstancesRequest.getReservations();
    }

    /**
     * @param keyName
     */
    public static void createKeyPair(String keyName) {
        CreateKeyPairRequest createKeyPairRequest = new CreateKeyPairRequest(
                keyName);
        ec2.createKeyPair(createKeyPairRequest);
        print("Created keypair: " + keyName);
    }

    /**
     * @return
     */
    public static int getKeyPairCount() {
        DescribeKeyPairsRequest describeKeyPairsRequest = new DescribeKeyPairsRequest();
        DescribeKeyPairsResult describeKeyPairsResult = ec2
                .describeKeyPairs(describeKeyPairsRequest);
        return describeKeyPairsResult.getKeyPairs().size();
    }

    /**
     * @param keyName
     */
    public static void deleteKeyPair(String keyName) {
        DeleteKeyPairRequest deleteKeyPairRequest = new DeleteKeyPairRequest(
                keyName);
        ec2.deleteKeyPair(deleteKeyPairRequest);
        print("Deleted keypair: " + keyName);
    }

    /**
     * Create a volume, returning the identifier.
     */
    public static String createVolume(final String zone, final int size) {
        final String volumeId = ec2.createVolume(
                new CreateVolumeRequest()
                        .withAvailabilityZone(zone)
                        .withSize(size)
        ).getVolume().getVolumeId();
        print("Created Volume: " + volumeId);
        return volumeId;
    }

    public static void deleteVolume(final String volumeId) {
        ec2.deleteVolume(new DeleteVolumeRequest().withVolumeId(volumeId));
        print("Deleted Volume: " + volumeId);
    }

    /**
     * Create a snapshot, returning the identifier.
     */
    public static String createSnapshot(final String volumeId, final String description) {
        final String snapshotId = ec2.createSnapshot(
                new CreateSnapshotRequest()
                        .withVolumeId(volumeId)
                        .withDescription(description)
        ).getSnapshot().getSnapshotId();
        print("Created Snapshot: " + snapshotId);
        return snapshotId;
    }

    public static void deleteSnapshot(final String snapshotId) {
        ec2.deleteSnapshot(new DeleteSnapshotRequest().withSnapshotId(snapshotId));
        print("Deleted Snapshot: " + snapshotId);
    }

    public static String allocateElasticIP() {
        final String ip = ec2.allocateAddress().getPublicIp();
        print("Allocated Elastic IP: " + ip);
        return ip;
    }

    public static void releaseElasticIP(final String ip) {
        ec2.releaseAddress(new ReleaseAddressRequest().withPublicIp(ip));
        print("Released Elastic IP: " + ip);
    }

    /**
     * @return
     */
    public static List<Instance> getLastlaunchedInstance() {
        List<Instance> instanceList = null;
        List<Reservation> reservations = getInstancesList();
        for (Reservation reservation : reservations) {
            instanceList = reservation.getInstances();
            Collections.sort(instanceList, new Comparator<Instance>() {
                public int compare(Instance i1, Instance i2) {
                    return i1.getLaunchTime().compareTo(i2.getLaunchTime());
                }
            });
        }
        return instanceList;
    }

    public static void createLaunchConfig(String launchConfig, String imageId, String instanceType, String keyName,
                                          String securityGroups, String kernelId, String ramdiskId,
                                          BlockDeviceMapping blockDeviceMapping, String iamInstanceProfile,
                                          InstanceMonitoring instanceMonitoring, String userData) {
        CreateLaunchConfigurationRequest createLaunchConfigurationRequest = new CreateLaunchConfigurationRequest()
                .withLaunchConfigurationName(launchConfig)
                .withImageId(imageId)
                .withInstanceType(instanceType)
                .withSecurityGroups(list(securityGroups))
                .withKeyName( keyName )
                .withKernelId( kernelId )
                .withRamdiskId( ramdiskId )
                .withBlockDeviceMappings(list(blockDeviceMapping))
                .withIamInstanceProfile( iamInstanceProfile )
                .withInstanceMonitoring( instanceMonitoring )
                .withUserData( userData );
        as.createLaunchConfiguration(createLaunchConfigurationRequest);
        print("Created Launch Configuration: " + launchConfig);
    }

    public static List<LaunchConfiguration> describeLaunchConfigs() {
        DescribeLaunchConfigurationsResult launchConfigurationsResult = null;
        try {
            DescribeLaunchConfigurationsRequest describeLaunchConfigurationsRequest = new DescribeLaunchConfigurationsRequest();
            launchConfigurationsResult = as.describeLaunchConfigurations(describeLaunchConfigurationsRequest);
        } catch (AmazonServiceException ase) {
            print(ase.getMessage());
        }
        return launchConfigurationsResult.getLaunchConfigurations();
    }

    public static void deleteLaunchConfig(String launchConfigurationName) {
        try {
            DeleteLaunchConfigurationRequest deleteLaunchConfigurationRequest = new DeleteLaunchConfigurationRequest()
                    .withLaunchConfigurationName(launchConfigurationName);
            as.deleteLaunchConfiguration(deleteLaunchConfigurationRequest);
            print("Deleted Launch Configuration: "
                    + launchConfigurationName);
        } catch (AmazonServiceException ase) {
            print(ase.getMessage());
        }
    }

    public static void createAutoScalingGroup(String groupName, String launchConfig, Integer minSize, Integer maxSize,
                                              Integer desiredCapacity, String availabilityZone, Integer cooldown,
                                              Integer healthCheckGracePeriod, String healthCheckType,
                                              String loadBalancer, Tag tag, String terminationPolicy) {
        CreateAutoScalingGroupRequest createAutoScalingGroupRequest = new CreateAutoScalingGroupRequest()
                .withAutoScalingGroupName(groupName)
                .withLaunchConfigurationName(launchConfig)
                .withMinSize(minSize)
                .withMaxSize(maxSize)
                .withDesiredCapacity(desiredCapacity)
                .withAvailabilityZones(list(availabilityZone))
                .withDefaultCooldown(cooldown)
                .withHealthCheckGracePeriod(healthCheckGracePeriod)
                .withHealthCheckType(healthCheckType)
                .withLoadBalancerNames(list(loadBalancer))
                .withTags(list(tag))
                .withTerminationPolicies(list(terminationPolicy));
        as.createAutoScalingGroup(createAutoScalingGroupRequest);
        print("Created Auto Scaling Group: " + groupName);
    }

    public static List<AutoScalingGroup> describeAutoScalingGroups() {
        DescribeAutoScalingGroupsResult autoScalingGroupsResult = null;
        try {
            DescribeAutoScalingGroupsRequest describeAutoScalingGroupsRequest = new DescribeAutoScalingGroupsRequest();
            autoScalingGroupsResult = as.describeAutoScalingGroups(describeAutoScalingGroupsRequest);
        } catch (AmazonServiceException ase) {
            print(ase.getMessage());
        }
        return autoScalingGroupsResult.getAutoScalingGroups();
    }

    public static void deleteAutoScalingGroup(String autoScalingGroupName, boolean force) {
        try {
            DeleteAutoScalingGroupRequest deleteAutoScalingGroupRequest = new DeleteAutoScalingGroupRequest()
                    .withAutoScalingGroupName(autoScalingGroupName)
                    .withForceDelete(force);
            as.deleteAutoScalingGroup(deleteAutoScalingGroupRequest);
            print("Deleted Auto Scaling Group: " + autoScalingGroupName);
        } catch (AmazonServiceException ase) {
            print(ase.getMessage());
        }
    }

    public static String getInstanceState(final String groupName) {
        final DescribeAutoScalingGroupsResult groupResult =
                as.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(groupName));
        String state = null;
        for (final AutoScalingGroup group : groupResult.getAutoScalingGroups()) {
            assertThat(groupName.equals(group.getAutoScalingGroupName()), "Unexpected group: " + group.getAutoScalingGroupName());
            assertThat(group.getInstances().size() < 2, "Unexpected instance count: " + group.getInstances().size());
            for (final com.amazonaws.services.autoscaling.model.Instance instance : group.getInstances()) {
                state = instance.getLifecycleState();
            }
        }
        return state;
    }

    public static void waitForInstances(final String state,
                                        final long timeout,
                                        final String groupName,
                                        final boolean allowEmpty) throws Exception {
        final long startTime = System.currentTimeMillis();
        boolean completed = false;
        String instanceState = null;
        while (!completed && (System.currentTimeMillis() - startTime) < timeout) {
            instanceState = getInstanceState(groupName);
            completed = instanceState == null && allowEmpty || state.equals(instanceState);
            Thread.sleep(2500);
        }
        assertThat(completed, "Instance not found with state " + state + " within the expected timeout");
        print("Instance found in " + (System.currentTimeMillis() - startTime) + "ms for state: " +
                state + (instanceState == null ? " (instance terminated before state detected)" : ""));
    }

    public static void deletePolicy(String policyName) {
        DeletePolicyRequest deletePolicyRequest = null;
        try {
            deletePolicyRequest = new DeletePolicyRequest().withPolicyName(policyName);
            as.deletePolicy(deletePolicyRequest);
            print("Deleted policy: " + policyName);
        } catch (AmazonServiceException ase) {
            print(ase.getMessage());
        }
    }

    public static List<ScalingPolicy> describePolicies() {
        DescribePoliciesResult describePoliciesResult = null;
        try {
            DescribePoliciesRequest describePoliciesRequest = new DescribePoliciesRequest();
            describePoliciesResult = as.describePolicies(describePoliciesRequest);
        } catch (AmazonServiceException ase) {
            print(ase.getMessage());
        }
        return describePoliciesResult.getScalingPolicies();
    }

    public static void createLoadBalancer(String loadBalancerName) {
        elb.createLoadBalancer(new CreateLoadBalancerRequest()
                .withLoadBalancerName(loadBalancerName)
                .withAvailabilityZones( list( AVAILABILITY_ZONE ) )
                .withListeners(
                    new Listener( ).withInstancePort( 80 )
                        .withLoadBalancerPort( 80 )
                        .withProtocol( "HTTP" ) ));
        print("Created load balancer: " + loadBalancerName);
    }

    public static void deleteLoadBlancer(String loadBalancerName) {
        try {
            elb.deleteLoadBalancer(new DeleteLoadBalancerRequest().withLoadBalancerName(loadBalancerName));
            print("Deleted load balancer: " + loadBalancerName);
        } catch (AmazonServiceException ase) {
            print(ase.getMessage());
        }
    }

    public static void waitForElbInstances(final String elbName, final long timeout, final List<String> instances)
            throws Exception {
        final long startTime = System.currentTimeMillis();
        boolean completed = false;
        while (!completed && (System.currentTimeMillis() - startTime) < timeout) {
            final List<String> elbInstances = new ArrayList<String>();
            final DescribeLoadBalancersResult balancersResult = elb.describeLoadBalancers(new DescribeLoadBalancersRequest().withLoadBalancerNames(elbName));
            for (final LoadBalancerDescription description : balancersResult.getLoadBalancerDescriptions()) {
                for (final com.amazonaws.services.elasticloadbalancing.model.Instance instance : description.getInstances()) {
                    elbInstances.add(instance.getInstanceId());
                }
            }
            completed = elbInstances.containsAll(instances) && instances.containsAll(elbInstances);
            Thread.sleep(2500);
        }
        assertThat(completed, "Instance not found for load balancer " + elbName + " within the expected timeout");
        print("Instance found in " + (System.currentTimeMillis() - startTime) + "ms for load balancer: " + elbName);
    }

    public static boolean isProfilePresent(final String profileName, final List<InstanceProfile> profiles) {
        boolean foundProfile = false;
        if (profiles != null) for (final InstanceProfile profile : profiles) {
            foundProfile = foundProfile || profileName.equals(profile.getInstanceProfileName());
        }
        return foundProfile;
    }

    public static void createIAMPolicy(final String accountName, String userName, String policyName, String policyDocument) {
        AWSCredentialsProvider awsCredentialsProvider = new StaticCredentialsProvider( new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
        final YouAreClient youAre = new YouAreClient(awsCredentialsProvider);
        youAre.setEndpoint(IAM_ENDPOINT);

        youAre.addRequestHandler(new AbstractRequestHandler() {
            public void beforeRequest(final Request<?> request) {
                request.addParameter("DelegateAccount", accountName);
            }
        });

        if (policyDocument == null) {
            policyDocument = "{\n" +
                    "\"Statement\": [\n" +
                    "  {\n" +
                    "   \"Action\": \"*\",\n" +
                    "   \"Effect\": \"Allow\",\n" +
                    "   \"Resource\": \"*\"\n" +
                    "   }\n" +
                    "  ]\n" +
                    "}";
        }
        PutUserPolicyRequest putUserPolicyRequest = new PutUserPolicyRequest()
                .withPolicyName(policyName)
                .withPolicyDocument(policyDocument)
                .withUserName(userName);
        youAre.putUserPolicy(putUserPolicyRequest);
        print("Created policy: " + policyName);
    }

    public static AWSCredentials getUserCreds(final String accountName, String userName) {
        AWSCredentialsProvider awsCredentialsProvider = new StaticCredentialsProvider( new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
        final YouAreClient youAre = new YouAreClient(awsCredentialsProvider);
        youAre.setEndpoint(IAM_ENDPOINT);

        youAre.addRequestHandler(new AbstractRequestHandler() {
            public void beforeRequest(final Request<?> request) {
                request.addParameter("DelegateAccount", accountName);
            }
        });

        CreateAccessKeyRequest createAccessKeyRequest = new CreateAccessKeyRequest().withUserName(userName);
        String newKeys = String.valueOf(youAre.createAccessKey(createAccessKeyRequest));
        print("Created new access key for user " + userName);

        // get accesskey from key gen result request
        int start = newKeys.lastIndexOf("AccessKeyId:") + 13;
        int end = newKeys.lastIndexOf(",Status");
        String accessKey = newKeys.substring(start, end);
        print("Access Key: " + accessKey);

        // get secretkey from key gen result request
        start = newKeys.lastIndexOf("SecretAccessKey:") + 17;
        end = newKeys.lastIndexOf(",CreateDate:");
        String secretKey = newKeys.substring(start, end);
        print("Secret Key: " + secretKey);

        return new BasicAWSCredentials(accessKey, secretKey);
    }

    public static void createAccount(String accountName) {
        int numAccountsBefore = youAre.listAccounts().getAccounts().size();
        CreateAccountRequest createAccountRequest = new CreateAccountRequest().withAccountName(accountName);
        youAre.createAccount(createAccountRequest);
        assertThat((numAccountsBefore < youAre.listAccounts().getAccounts().size()),"Failed to create account " + accountName);
        print("Created account: " + accountName);
    }

    public static void deleteAccount(String accountName){
        int numAccountsBefore = youAre.listAccounts().getAccounts().size();
        DeleteAccountRequest deleteAccountRequest = new DeleteAccountRequest().withAccountName(accountName).withRecursive(Boolean.TRUE);
        youAre.deleteAccount(deleteAccountRequest);
        assertThat((numAccountsBefore > youAre.listAccounts().getAccounts().size()),"Failed to delete account " + accountName);
        print("Deleted account: " + accountName);

    }

    public static void createUser(final String accountName, String userName){
        AWSCredentialsProvider awsCredentialsProvider = new StaticCredentialsProvider( new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
        final YouAreClient youAre = new YouAreClient(awsCredentialsProvider);
        youAre.setEndpoint(IAM_ENDPOINT);

        youAre.addRequestHandler(new AbstractRequestHandler() {
            public void beforeRequest(final Request<?> request) {
                request.addParameter("DelegateAccount", accountName);
            }
        });

        int numUsersBefore = youAre.listUsers().getUsers().size();
        CreateUserRequest createUserRequest = new CreateUserRequest()
                .withUserName(userName)
                .withPath("/");
        youAre.createUser(createUserRequest);

        assertThat((numUsersBefore < youAre.listUsers().getUsers().size()), "Failed to create user " + userName);
        print("Created new user " + userName + " in account " + accountName);
    }

    public static Map<String, String> getUserKeys(final String accountName, String userName){
        Map<String, String> keys = new HashMap<>();

        AWSCredentialsProvider awsCredentialsProvider = new StaticCredentialsProvider( new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
        final YouAreClient youAre = new YouAreClient(awsCredentialsProvider);
        youAre.setEndpoint(IAM_ENDPOINT);

        youAre.addRequestHandler(new AbstractRequestHandler() {
            public void beforeRequest(final Request<?> request) {
                request.addParameter("DelegateAccount", accountName);
            }
        });

        CreateAccessKeyRequest createAccessKeyRequest = new CreateAccessKeyRequest().withUserName(userName);
        String newKeys = String.valueOf(youAre.createAccessKey(createAccessKeyRequest));
        print("Created new access key for user " + userName);

        // get accesskey from key gen result request
        int start = newKeys.lastIndexOf("AccessKeyId:") + 13;
        int end = newKeys.lastIndexOf(",Status");
        String accessKey = newKeys.substring(start, end);
        keys.put("ak", accessKey);

        // get secretkey from key gen result request
        start = newKeys.lastIndexOf("SecretAccessKey:") + 17;
        end = newKeys.lastIndexOf(",CreateDate:");
        String secretKey = newKeys.substring(start, end);
        keys.put("sk", secretKey);

        return keys;
    }

    public static void deleteTestCreds(String accesskey, String remoteIniFile) {
        print("Deleting accesskey " + accesskey);
        youAre.deleteAccessKey(new DeleteAccessKeyRequest("admin", accesskey));
        print("Deleting remote file " + remoteIniFile + " from the CLC");
        removeRemoteFile(CLC_IP, USER, PASSWORD, remoteIniFile);
    }

    @SafeVarargs
    private static <T> List<T> list( T... ts ) {
        return ts == null || (ts.length == 1 && ts[0] == null) ? Collections.<T>emptyList( ) : Arrays.<T>asList( ts );
    }
}