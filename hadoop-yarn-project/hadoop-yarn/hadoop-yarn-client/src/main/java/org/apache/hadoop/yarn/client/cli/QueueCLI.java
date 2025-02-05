/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.yarn.client.cli;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.classification.InterfaceAudience.Private;
import org.apache.hadoop.classification.InterfaceStability.Unstable;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.yarn.api.records.NodeLabel;
import org.apache.hadoop.yarn.api.records.QueueInfo;
import org.apache.hadoop.yarn.client.util.FormattingCLIUtils;
import org.apache.hadoop.yarn.exceptions.YarnException;

import org.apache.hadoop.classification.VisibleForTesting;

import static org.apache.hadoop.yarn.client.util.YarnClientUtils.isYarnFederationEnabled;

@Private
@Unstable
public class QueueCLI extends YarnCLI {
  public static final String QUEUE = "queue";

  public static final String ALLTAG = "all";

  public static void main(String[] args) throws Exception {
    QueueCLI cli = new QueueCLI();
    cli.setSysOutPrintStream(System.out);
    cli.setSysErrPrintStream(System.err);
    int res = ToolRunner.run(cli, args);
    cli.stop();
    System.exit(res);
  }

  @Override
  public int run(String[] args) throws Exception {
    Options opts = new Options();

    opts.addOption(STATUS_CMD, true,
        "List queue information about given queue.");
    opts.addOption(HELP_CMD, false, "Displays help for all commands.");
    opts.getOption(STATUS_CMD).setArgName("Queue Name");
    opts.addOption(LIST_CMD, true,
         "All child queues are displayed according to the parent queue. " +
         "If the value is all, all queues are displayed.");
    opts.getOption(LIST_CMD).setArgName("Parent Queue Name");
    opts.addOption(OPTION_SUBCLUSTERID, true, "We support setting subClusterId in " +
        "YARN Federation mode to specify specific subClusters.");

    CommandLine cliParser = null;
    try {
      cliParser = new GnuParser().parse(opts, args);
    } catch (MissingArgumentException ex) {
      sysout.println("Missing argument for options");
      printUsage(opts);
      return -1;
    }
    createAndStartYarnClient();
    if (cliParser.hasOption(STATUS_CMD)) {
      // Our possible options are -status root.a,
      // -subcluster sc-1, we will have up to 4 args
      if (args.length > 4) {
        printUsage(opts);
        return -1;
      }
      String queue = cliParser.getOptionValue(STATUS_CMD);
      String subClusterId = cliParser.getOptionValue(OPTION_SUBCLUSTERID);
      if (isYarnFederationEnabled(getConf()) && StringUtils.isNotBlank(subClusterId)) {
        return listQueue(queue, subClusterId);
      } else {
        return listQueue(queue);
      }
    } else if (cliParser.hasOption(HELP_CMD)) {
      printUsage(opts);
      return 0;
    } else if (cliParser.hasOption(LIST_CMD)) {
      if (args.length != 2) {
        printUsage(opts);
        return -1;
      }
      return listChildQueues(cliParser.getOptionValue(LIST_CMD));
    } else {
      syserr.println("Invalid Command Usage : ");
      printUsage(opts);
      return -1;
    }
  }

  /**
   * It prints the usage of the command
   * 
   * @param opts
   */
  @VisibleForTesting
  void printUsage(Options opts) {
    new HelpFormatter().printHelp(QUEUE, opts);
  }
  
  /**
   * Lists the Queue Information matching the given queue name.
   * 
   * @param queueName Queue name to be queried.
   * @throws YarnException YarnException indicates exceptions from yarn servers.
   * @throws IOException I/O exception has occurred.
   * @return 0, the command execution is successful; -1, the command execution fails.
   */
  private int listQueue(String queueName) throws YarnException, IOException {
    int rc;
    PrintWriter writer = new PrintWriter(
        new OutputStreamWriter(sysout, Charset.forName("UTF-8")));

    QueueInfo queueInfo = client.getQueueInfo(queueName);
    if (queueInfo != null) {
      if (isYarnFederationEnabled(getConf())) {
        writer.println("Using YARN Federation mode.");
      }
      writer.println("Queue Information : ");
      printQueueInfo(writer, queueInfo);
      rc = 0;
    } else {
      writer.println("Cannot get queue from RM by queueName = " + queueName
          + ", please check.");
      rc = -1;
    }
    writer.flush();
    return rc;
  }

  /**
   * Lists the Queue Information matching the given queue name.
   *
   * @param queueName Queue name to be queried.
   * @param subClusterId Subcluster id.
   * @throws YarnException YarnException indicates exceptions from yarn servers.
   * @throws IOException I/O exception has occurred.
   * @return 0, the command execution is successful; -1, the command execution fails.
   */
  private int listQueue(String queueName, String subClusterId)
      throws YarnException, IOException {
    int rc;
    PrintWriter writer = new PrintWriter(
        new OutputStreamWriter(sysout, Charset.forName("UTF-8")));
    QueueInfo queueInfo = client.getQueueInfo(queueName, subClusterId);
    if (queueInfo != null) {
      if (isYarnFederationEnabled(getConf())) {
        writer.println("Using YARN Federation mode.");
      }
      if (StringUtils.isNotBlank(subClusterId)) {
        writer.println("SubClusterId : " + subClusterId + ", Queue Information : ");
      } else {
        writer.println("Queue Information : ");
      }
      printQueueInfo(writer, queueInfo);
      rc = 0;
    } else {
      writer.println("Cannot get queue from RM by queueName = " + queueName
          + ", subClusterId = " + subClusterId + " please check.");
      rc = -1;
    }
    writer.flush();
    return rc;
  }

  /**
   * List information about all child queues based on the parent queue.
   * @param parentQueueName The name of the payment queue.
   * @return The status code of execution.
   * @throws IOException failed or interrupted I/O operations.
   * @throws YarnException exceptions from yarn servers.
   */
  private int listChildQueues(String parentQueueName) throws IOException, YarnException {
    int exitCode;
    PrintWriter writer = new PrintWriter(new OutputStreamWriter(
        sysout, Charset.forName(StandardCharsets.UTF_8.name())));
    if (parentQueueName.equalsIgnoreCase(ALLTAG)) {
      List<QueueInfo> queueInfos = client.getAllQueues();
      if (queueInfos != null) {
        printQueueInfos(writer, queueInfos);
        exitCode = 0;
      } else {
        writer.println("Cannot get any queues from RM,please check.");
        exitCode = -1;
      }
    } else {
      List<QueueInfo> childQueueInfos = client.getChildQueueInfos(parentQueueName);
      if (childQueueInfos != null) {
        printQueueInfos(writer, childQueueInfos);
        exitCode = 0;
      } else {
        writer.println("Cannot get any queues under " + parentQueueName + " from RM,please check.");
        exitCode = -1;
      }
    }
    writer.flush();
    return exitCode;
  }

  private void printQueueInfo(PrintWriter writer, QueueInfo queueInfo) {
    String schedulerType = queueInfo.getSchedulerType();

    if (StringUtils.equals("FairScheduler", schedulerType)) {
      printFairSchedulerQueue(writer, queueInfo);
    } else {
      printQueue(writer, queueInfo);
    }
  }

  /**
   * Print Queue information of FairScheduler.
   *
   * @param writer PrintWriter.
   * @param queueInfo Queue Information.
   */
  private void printFairSchedulerQueue(PrintWriter writer, QueueInfo queueInfo) {
    String generateQueueInfoMessage = generateQueueInfoMessage(queueInfo);
    writer.print(generateQueueInfoMessage);
  }

  private String generateQueueInfoMessage(QueueInfo queueInfo) {
    StringBuilder stringBuilder = new StringBuilder();
    if (queueInfo.getSchedulerType() != null) {
      stringBuilder.append("Scheduler Name : ").append(queueInfo.getSchedulerType()).append("\n");
    }
    stringBuilder.append("Queue Name : ").append(queueInfo.getQueueName()).append("\n");
    DecimalFormat df = new DecimalFormat("0.00");
    stringBuilder.append("\tWeight : ").append(df.format(queueInfo.getWeight())).append("\n");
    stringBuilder.append("\tState : ").append(queueInfo.getQueueState()).append("\n");
    stringBuilder.append("\tMinResource : ").append("<memory : ")
        .append(queueInfo.getMinResourceMemory()).append(", vCores:")
        .append(queueInfo.getMinResourceVCore()).append(">").append("\n");
    stringBuilder.append("\tMaxResource : ").append("<memory : ")
        .append(queueInfo.getMaxResourceMemory()).append(", vCores:")
        .append(queueInfo.getMaxResourceVCore()).append(">").append("\n");
    stringBuilder.append("\tReservedResource : ").append("<memory : ")
        .append(queueInfo.getReservedResourceMemory()).append(", vCores:")
        .append(queueInfo.getReservedResourceVCore()).append(">").append("\n");
    stringBuilder.append("\tSteadyFairShare : ").append("<memory : ")
        .append(queueInfo.getSteadyFairShareMemory()).append(", vCores:")
        .append(queueInfo.getSteadyFairShareVCore()).append(">").append("\n");
    Boolean queuePreemption = queueInfo.getPreemptionDisabled();
    if (queuePreemption != null) {
      stringBuilder.append("\tQueue Preemption : ")
          .append(queuePreemption ? "enabled" : "disabled").append("\n");
    }
    return stringBuilder.toString();
  }

  /**
   * Print Queue information.
   *
   * @param writer PrintWriter.
   * @param queueInfo Queue Information.
   */
  private void printQueue(PrintWriter writer, QueueInfo queueInfo) {
    if (queueInfo.getSchedulerType() != null) {
      writer.print("Scheduler Name : ");
      writer.println(queueInfo.getSchedulerType());
    }
    writer.print("Queue Name : ");
    writer.println(queueInfo.getQueueName());
    writer.print("Queue Path : ");
    writer.println(queueInfo.getQueuePath());

    writer.print("\tState : ");
    writer.println(queueInfo.getQueueState());
    DecimalFormat df = new DecimalFormat("#.00");
    writer.print("\tCapacity : ");
    writer.println(df.format(queueInfo.getCapacity() * 100) + "%");
    writer.print("\tCurrent Capacity : ");
    writer.println(df.format(queueInfo.getCurrentCapacity() * 100) + "%");
    writer.print("\tMaximum Capacity : ");
    writer.println(df.format(queueInfo.getMaximumCapacity() * 100) + "%");
    writer.print("\tWeight : ");
    writer.println(df.format(queueInfo.getWeight()));
    writer.print("\tMaximum Parallel Apps : ");
    writer.println(queueInfo.getMaxParallelApps());
    writer.print("\tDefault Node Label expression : ");
    String nodeLabelExpression = queueInfo.getDefaultNodeLabelExpression();
    nodeLabelExpression =
        (nodeLabelExpression == null || nodeLabelExpression.trim().isEmpty())
            ? NodeLabel.DEFAULT_NODE_LABEL_PARTITION : nodeLabelExpression;
    writer.println(nodeLabelExpression);

    Set<String> nodeLabels = queueInfo.getAccessibleNodeLabels();
    StringBuilder labelList = new StringBuilder();
    writer.print("\tAccessible Node Labels : ");
    for (String nodeLabel : nodeLabels) {
      if (labelList.length() > 0) {
        labelList.append(',');
      }
      labelList.append(nodeLabel);
    }
    writer.println(labelList);

    Boolean preemptStatus = queueInfo.getPreemptionDisabled();
    if (preemptStatus != null) {
      writer.print("\tPreemption : ");
      writer.println(preemptStatus ? "disabled" : "enabled");
    }

    Boolean intraQueuePreemption = queueInfo.getIntraQueuePreemptionDisabled();
    if (intraQueuePreemption != null) {
      writer.print("\tIntra-queue Preemption : ");
      writer.println(intraQueuePreemption ? "disabled" : "enabled");
    }
  }

  private void printQueueInfos(PrintWriter writer, List<QueueInfo> queueInfos) {
    String titleString = queueInfos.size() + " queues were found";
    List<String> headerStrings = Arrays.asList("Queue Name", "Queue Path", "State", "Capacity",
        "Current Capacity", "Maximum Capacity", "Weight", "Maximum Parallel Apps");
    FormattingCLIUtils formattingCLIUtils = new FormattingCLIUtils(titleString)
        .addHeaders(headerStrings);
    DecimalFormat df = new DecimalFormat("#.00");
    for (QueueInfo queueInfo : queueInfos) {
      formattingCLIUtils.addLine(queueInfo.getQueueName(), queueInfo.getQueuePath(),
          queueInfo.getQueueState(), df.format(queueInfo.getCapacity() * 100) + "%",
          df.format(queueInfo.getCurrentCapacity() * 100) + "%",
          df.format(queueInfo.getMaximumCapacity() * 100) + "%",
          df.format(queueInfo.getWeight()),
          queueInfo.getMaxParallelApps());
    }
    writer.print(formattingCLIUtils.render());
  }
}
