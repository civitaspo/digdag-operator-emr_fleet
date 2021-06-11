# digdag-operator-emr_fleet
[![Jitpack](https://jitpack.io/v/pro.civitaspo/digdag-operator-emr_fleet.svg)](https://jitpack.io/#pro.civitaspo/digdag-operator-emr_fleet) [![CircleCI](https://circleci.com/gh/civitaspo/digdag-operator-emr_fleet.svg?style=shield)](https://circleci.com/gh/civitaspo/digdag-operator-emr_fleet) [![Digdag](https://img.shields.io/badge/digdag-v0.9.27-brightgreen.svg)](https://github.com/treasure-data/digdag/releases/tag/v0.9.27)

This operator is for operating a cluster with instance fleets on Amazon Elastic Map Reduce.

# Overview

- Plugin type: operator

# Usage


```
_export:
  plugin:
    repositories:
      - https://jitpack.io
    dependencies:
      - pro.civitaspo:digdag-operator-emr_fleet:0.0.5
  emr_fleet:
    auth_method: profile

+step1:
  emr_fleet.create_cluster>:
  tags:
    environment: test
  spot_spec:
    block_duration: 1h
  master_fleet:
    bid_percentage: 60
    candidates:
      - instance_type: r3.xlarge
      - instance_type: m3.xlarge
  core_fleet:
    target_capacity: 192
    bid_percentage: 60
    candidates:
      - instance_type: r3.8xlarge
        spot_units: 32
        ebs:
          optimized: false
      - instance_type: r3.4xlarge
        spot_units: 16
  applications: [Hadoop, Spark, Livy]
  configurations:
    - classification: spark-defaults
      properties:
        maximizeResourceAllocation: true
    - classification: capacity-scheduler
      properties:
        yarn.scheduler.capacity.resource-calculator: org.apache.hadoop.yarn.util.resource.DominantResourceCalculator

+step2:
  echo>: ${emr_fleet.last_cluster}

+step3:
  emr_fleet.detect_clusters>:
  created_within: 1h
  regexp: .*${session_uuid}.*

+step4:
  emr_fleet.shutdown_cluster>: ${emr_fleet.last_cluster.id}

+step5:
  emr_fleet.wait_cluster>: ${emr_fleet.last_cluster.id}
  success_states: [TERMINATED]
  _error:
    emr_fleet.shutdown_cluster>: ${emr_fleet.last_cluster.id}
```

# Concept

* Configuable for complicated EMR Cluster
* Available for credentials on servers if server administrators allow the operation
* Small processing that a operator has

# Configuration

## Remarks

- An EMR Cluster is also called an job flow. In the below explanations, I use both terms.
- type `DurationParam` is strings matched `\s*(?:(?<days>\d+)\s*d)?\s*(?:(?<hours>\d+)\s*h)?\s*(?:(?<minutes>\d+)\s*m)?\s*(?:(?<seconds>\d+)\s*s)?\s*`.
  - The strings is used as `java.time.Duration`.

## Common Configuration

### System Options

Define the below options on properties (which is indicated by `-c`, `--config`).

- **emr_fleet.allow_auth_method_env**: Indicates whether users can use **auth_method** `"env"` (boolean, default: `false`)
- **emr_fleet.allow_auth_method_instance**: Indicates whether users can use **auth_method** `"instance"` (boolean, default: `false`)
- **emr_fleet.allow_auth_method_profile**: Indicates whether users can use **auth_method** `"profile"` (boolean, default: `false`)
- **emr_fleet.allow_auth_method_properties**: Indicates whether users can use **auth_method** `"properties"` (boolean, default: `false`)
- **emr_fleet.assume_role_timeout_duration**: Maximum duration which server administer allows when users assume **role_arn**. (`DurationParam`, default: `1h`)

### Secrets

- **emr_fleet.access_key_id**: The AWS Access Key ID to use when submitting EMR jobs. (optional)
- **emr_fleet.secret_access_key**: The AWS Secret Access Key to use when submitting EMR jobs. (optional)
- **emr_fleet.session_token**: The AWS session token to use when submitting EMR jobs. This is used only **auth_method** is `"session"` (optional)
- **emr_fleet.role_arn**: The AWS Role to assume when submitting EMR jobs. (optional)
- **emr_fleet.role_session_name**: The AWS Role Session Name when assuming the role. (default: `digdag-emr_fleet-${session_uuid}`)
- **emr_fleet.http_proxy.host**: proxy host (required if **use_http_proxy** is `true`)
- **emr_fleet.http_proxy.port** proxy port (optional)
- **emr_fleet.http_proxy.scheme** `"https"` or `"http"` (default: `"https"`)
- **emr_fleet.http_proxy.user** proxy user (optional)
- **emr_fleet.http_proxy.password**: http proxy password (optional)

### Options

- **auth_method**: name of mechanism to authenticate requests (`"basic"`, `"env"`, `"instance"`, `"profile"`, `"properties"`, `"anonymous"`, or `"session"`. default: `"basic"`)
  - `"basic"`: uses access_key_id and secret_access_key to authenticate.
  - `"env"`: uses AWS_ACCESS_KEY_ID (or AWS_ACCESS_KEY) and AWS_SECRET_KEY (or AWS_SECRET_ACCESS_KEY) environment variables.
  - `"instance"`: uses EC2 instance profile.
  - `"profile"`: uses credentials written in a file. Format of the file is as following, where `[...]` is a name of profile.
    - **profile_file**: path to a profiles file. (string, default: given by AWS_CREDENTIAL_PROFILES_FILE environment varialbe, or ~/.aws/credentials).
    - **profile_name**: name of a profile. (string, default: `"default"`)
  - `"properties"`: uses aws.accessKeyId and aws.secretKey Java system properties.
  - `"anonymous"`: uses anonymous access. This auth method can access only public files.
  - `"session"`: uses temporary-generated access_key_id, secret_access_key and session_token.
- **use_http_proxy**: Indicate whether using when accessing AWS via http proxy. (boolean, default: `false`)
- **region**: The AWS region to use for EMR service. (string, optional)
- **endpoint**: The AWS EMR endpoint address to use. (string, optional)

## Configuration for `emr_fleet.create_cluster>` operator

### Options

- **name**: The name of the job flow. (string, default: `"digdag-${session_uuid}"`)
- **tags**: A list of tags to associate with a cluster and propagate to Amazon EC2 instances. (string to string map, optional)
- **release_label**: The Amazon EMR release label, which determines the version of open-source application packages installed on the cluster. Release labels are in the form emr-x.x.x, where x.x.x is an Amazon EMR release version. For more information about Amazon EMR release versions and included application versions and features, see [Docs](https://docs.aws.amazon.com/emr/latest/ReleaseGuide/). (string, default: `emr-5.16.0` )
- **custom_ami_id**: Available only in Amazon EMR version 5.7.0 and later. The ID of a custom Amazon EBS-backed Linux AMI. If specified, Amazon EMR uses this AMI when it launches cluster EC2 instances. (string, optional)
- **master_security_groups**: A list of Amazon EC2 security group IDs for the master node. The first one is used as Amazon EMR–managed security group, and the left is used as additional one. See [Docs](https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-security-groups.html) to get more information. (array of string, optional)
- **slave_security_groups**: A list of Amazon EC2 security group IDs for the slave node. The first one is used as Amazon EMR–managed security group, and the left is used as additional one. See [Docs](https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-security-groups.html) to get more information. (array of string, optional)
- **ssh_key**: The name of the EC2 key pair that can be used to ssh to the nodes. (string, optional)
- **subnet_ids**: Applies to clusters that use the instance fleet configuration. When multiple EC2 subnet IDs are specified, Amazon EMR evaluates them and launches instances in the optimal subnet. (array of string, optional)
- **availability_zones**: When multiple Availability Zones are specified, Amazon EMR evaluates them and launches instances in the optimal Availability Zone. (array of string, optional)
- **spot_spec**: The launch specification for Spot instances in the instance fleet, which determines the defined duration and provisioning timeout behavior. (map, default: `{}`)
  - **block_duration**: The defined duration for Spot instances (also known as Spot blocks). When specified, the Spot instance does not terminate before the defined duration expires, and defined duration pricing for Spot instances applies. Current valid values are `"1h"`, `"2h"`, `"3h"`, `"4h"`, `"5h"`, or `"6h"`. The duration period starts as soon as a Spot instance receives its instance ID. At the end of the duration, Amazon EC2 marks the Spot instance for termination and provides a Spot instance termination notice, which gives the instance a two-minute warning before it terminates. (`DurationParam`, optional)
  - **timeout_action**: The action to take when **target_spot_capacity** has not been fulfilled when the **timeout_duration** has expired. Spot instances are not uprovisioned within the Spot provisioining timeout. Valid values are `TERMINATE_CLUSTER` and `SWITCH_TO_ON_DEMAND`. `SWITCH_TO_ON_DEMAND` specifies that if no Spot instances are available, On-Demand Instances should be provisioned to fulfill any remaining Spot capacity. (string, default: `TERMINATE_CLUSTER`)
  - **timeout_duration**: The spot provisioning timeout period. If Spot instances are not provisioned within this time period, the **timeout_action** is taken. Minimum value is `"5m"` and maximum value is `"1d"`. The timeout applies only during initial provisioning, when the cluster is first created. (`DurationParam`, default: `45m`)
  - **allocation_strategy**: The allocation strategy provisions Spot Instances. This option is available for EMR versions 5.12.1 and later, see [Docs](https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-instance-fleet.html). Valid value is only `CapacityOptimized`. (string, optional)
- **master_fleet**: Describes the EC2 instances and instance configurations for master node that use the instance fleet configuration. (map, required)
  - **name**: The friendly name of the instance fleet. (string, default: `master instance fleet`)
  - **use_spot_instance**: Indicates whether master node uses Spot instance or On-Demand instance. (boolean, default: `true`)
  - **bid_percentage**: The bid price, as a percentage of On-Demand price, for each EC2 Spot instance as defined by **instance_type**. Expressed as a number (for example, 20 specifies 20%). This value is used for **candidates**'s **bid_percentage** default value. (double, default: `100.0`)
  - **candidates**: An instance type configuration for each instance type in an instance fleet, which determines the EC2 instances Amazon EMR attempts to provision to fulfill On-Demand and Spot target capacities. There can be a maximum of 5 instance type configurations in a fleet. (array of map, required)
    - **bid_price**: The bid price for each EC2 Spot instance type as defined by **instance_type**. Expressed in USD. If neither **bid_price** nor **bid_percentage** is provided, **bid_percentage** defaults to 100%. (string, optional)
    - **bid_percentage**: The bid price, as a percentage of On-Demand price, for each EC2 Spot instance as defined by **instance_type**. Expressed as a number (for example, 20 specifies 20%). If neither **bid_price** nor **bid_percentage** is provided, **bid_percentage** defaults to 100%. (double, default: `${master_fleet.bid_percentage}`)
    - **instance_type**: An EC2 instance type, such as `m3.xlarge`. (string, required)
    - **configurations**: A configuration classification that applies when provisioning cluster instances, which can include configurations for applications and software that run on the cluster. (array of map, optional)
      - **classification**: The classification within a configuration. (string, required)
      - **properties**: A set of properties specified within a configuration classification. (string to string map, default: optional)
      - **configurations**: A list of additional configurations to apply within a configuration object. (array of this configurations object, optional)
    - **ebs**: The Amazon EBS configuration of a cluster instance. (map, default: `{}`)
      - **optimized**: Indicates whether an Amazon EBS volume is EBS-optimized. (boolean, default: `true`)
      - **iops**: The number of I/O operations per second (IOPS) that the volume supports. (integer, optional)
      - **size**: The volume size, in gibibytes (GiB). This can be a number from 1 - 1024. If the volume type is EBS-optimized, the minimum value is 10. (integer, default: `256`)
      - **type**: The volume type. Volume types supported are gp2, io1, standard. (string, default: `gp2`)
      - **volumes_per_instance**: Number of EBS volumes with a specific volume configuration that will be associated with every instance in the instance group. (integer, default: `1`)
- **core_fleet**: Describes the EC2 instances and instance configurations for core nodes that use the instance fleet configuration. (map, required)
  - **name**: The friendly name of the instance fleet. (string, default: `core instance fleet`)
  - **target_capacity**: The target capacity of **spot_units** for the instance fleet, which determines how many Spot instances to provision. When the instance fleet launches, Amazon EMR tries to provision Spot instances as specified by **candidates** settings. Each instance configuration has a specified **spot_units**. When a Spot instance is provisioned, the **spot_units** count toward the target capacity. Amazon EMR provisions instances until the target capacity is totally fulfilled, even if this results in an overage. (integer, required)
  - **bid_percentage**: The bid price, as a percentage of On-Demand price, for each EC2 Spot instance as defined by **instance_type**. Expressed as a number (for example, 20 specifies 20%). This value is used for **candidates**'s **bid_percentage** default value. (double, default: `100.0`)
  - **candidates**: An instance type configuration for each instance type in an instance fleet, which determines the EC2 instances Amazon EMR attempts to provision to fulfill On-Demand and Spot target capacities. There can be a maximum of 5 instance type configurations in a fleet. (array of map, required)
    - **bid_price**: The bid price for each EC2 Spot instance type as defined by **instance_type**. Expressed in USD. If neither **bid_price** nor **bid_percentage** is provided, **bid_percentage** defaults to 100%. (string, optional)
    - **bid_percentage**: The bid price, as a percentage of On-Demand price, for each EC2 Spot instance as defined by **instance_type**. Expressed as a number (for example, 20 specifies 20%). If neither **bid_price** nor **bid_percentage** is provided, **bid_percentage** defaults to 100%. (double, default: `${core_fleet.bid_percentage}`)
    - **instance_type**: An EC2 instance type, such as `m3.xlarge`. (string, required)
    - **configurations**: A configuration classification that applies when provisioning cluster instances, which can include configurations for applications and software that run on the cluster. (array of map, optional)
      - **classification**: The classification within a configuration. (string, required)
      - **properties**: A set of properties specified within a configuration classification. (string to string map, optional)
      - **configurations**: A list of additional configurations to apply within a configuration object. (array of this configurations object, optional)
    - **ebs**: The Amazon EBS configuration of a cluster instance. (map, default: `{}`)
      - **optimized**: Indicates whether an Amazon EBS volume is EBS-optimized. (boolean, default: `true`)
      - **iops**: The number of I/O operations per second (IOPS) that the volume supports. (integer, optional)
      - **size**: The volume size, in gibibytes (GiB). This can be a number from 1 - 1024. If the volume type is EBS-optimized, the minimum value is 10. (integer, default: `256`)
      - **type**: The volume type. Volume types supported are gp2, io1, standard. (string, default: `gp2`)
      - **volumes_per_instance**: Number of EBS volumes with a specific volume configuration that will be associated with every instance in the instance group. (integer, default: `1`)
    - **spot_units**: The number of units that a provisioned instance of this type provides toward fulfilling the **target_capacity**. This value must be 1 or greater. (integer, default: `1`)
- **task_fleet**: Describes the EC2 instances and instance configurations for task nodes that use the instance fleet configuration. (map, optional)
  - **name**: The friendly name of the instance fleet. (string, default: `task instance fleet`)
  - The other options are the same as **task** option.
- **log_uri**: The location in Amazon S3 to write the log files of the job flow. If a value is not provided, logs are not created. (string, optional)
- **additional_info**: A JSON string for selecting additional features. (string, optional)
- **visible**: Whether the cluster is visible to all IAM users of the AWS account associated with the cluster. If this value is set to true, all IAM users of that AWS account can view and (if they have the proper policy permissions set) manage the cluster. If it is set to false, only the IAM user that created the cluster can view and manage it. (boolean, default: `true`)
- **security_configuration**: The name of a security configuration to apply to the cluster. (string, optional)
- **instance_profile**: Also called job flow role and EC2 role. An IAM role for an EMR cluster. The EC2 instances of the cluster assume this role. The default role is EMR_EC2_DefaultRole. In order to use the default role, you must have already created it using the CLI or console. (string, default: `EMR_EC2_DefaultRole`)
- **service_role**: The IAM role that will be assumed by the Amazon EMR service to access AWS resources on your behalf. (string, default: `EMR_DefaultRole`)
- **applications**: A list of applications for the cluster. (array of string, default: `["Hadoop"]`)
- **configurations**: The list of configurations supplied for the EMR cluster you are creating. See [Docs](https://docs.aws.amazon.com/emr/latest/ReleaseGuide/emr-configure-apps.html) to get more information. (array of map, optional)
  - **classification**: The classification within a configuration. (string, required)
  - **properties**: A set of properties specified within a configuration classification. (string to string map, optional)
  - **configurations**: A list of additional configurations to apply within a configuration object. (array of this configurations object, optional)
- **bootstrap_actions**: A list of bootstrap actions to run before Hadoop starts on the cluster nodes. (array of map, optional)
  - **name**: The name of the bootstrap action. (string, required)
  - **script**: The script location to run during a bootstrap action. Can be either a location in Amazon S3 or on a local file system of EMR Servers. (string, required)
  - **args**: A list of command line arguments to pass to the bootstrap action script. (array of string, default: `[]`)
  - **content**: The script content or local file location for the content. If this option is defined, write the content into the **script** object on S3. (string, optional)
  - **run_if**: Specify the condition if the bootstrap action should be run conditionally. The condition format is the same as `s3://elasticmapreduce/bootstrap-actions/run-if`. See [Doc](https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-plan-bootstrap.html#emr-bootstrap-runif) (string, optional)
- **keep_alive_when_no_steps**: Specifies whether the cluster should remain available after completing all steps. (boolean, default: `true`)
- **termination_protected**: Specifies whether to lock the cluster to prevent the Amazon EC2 instances from being terminated by API call, user intervention, or in the event of a job-flow error. (boolean, default: `false`)
- **wait_available_state**: Specifies whether to wait until the cluster becomes available after created. Available state means `"RUNNING"` or `"WAITING"`. (boolean, default: `true`)
- **wait_timeout_duration**: Specify timeout period. (`DurationParam`, default: `"45m"`)

### Output parameters

- **emr_fleet.last_cluster.id**: The ID of the cluster created. (string)
- **emr_fleet.last_cluster.master.instance_id**: The instance id of the cluster master node. You can get this if **wait_available_state** is `true`. (string)
- **emr_fleet.last_cluster.master.instance_type**: The ip of the cluster master node. You can get this if **wait_available_state** is `true`. (string)
- **emr_fleet.last_cluster.master.market**: The market of the cluster master node. You can get this if **wait_available_state** is `true`. (string)
- **emr_fleet.last_cluster.master.private_dns_name**: The private dns name of the cluster master node. You can get this if **wait_available_state** is `true`. (string)
- **emr_fleet.last_cluster.master.private_ip_address**: The private ip address of the cluster master node. You can get this if **wait_available_state** is `true`. (string)
- **emr_fleet.last_cluster.master.public_dns_name**: The public dns name of the cluster master node. You can get this if **wait_available_state** is `true`. (string)
- **emr_fleet.last_cluster.master.public_ip_address**: The public ip address of the cluster master node. You can get this if **wait_available_state** is `true`. (string)

## Configuration for `emr_fleet.wait_cluster>` operator

### Options

- **emr_fleet.wait_cluster>**: Specifies either the ID of an existing cluster (string, required)
- **success_states**: The cluster states breaks polling the cluster. Valid values are `"STARTING"`, `"BOOTSTRAPPING"`, `"RUNNING"`, `"WAITING"`, `"TERMINATING"`, `"TERMINATED"` and `"TERMINATED_WITH_ERRORS"`. (array of string, required)
- **error_states**: The cluster states breaks polling the cluster with errors. Valid values are `"STARTING"`, `"BOOTSTRAPPING"`, `"RUNNING"`, `"WAITING"`, `"TERMINATING"`, `"TERMINATED"` and `"TERMINATED_WITH_ERRORS"`. (array of string, optional)
- **polling_interval**: Specify polling interval. (`DurationParam`, default: `"5s"`)
- **timeout_duration**: Specify timeout period. (`DurationParam`, default: `"45m"`)

### Output parameters

- **emr_fleet.last_cluster.id**: The ID of the cluster created. (string)
- **emr_fleet.last_cluster.master.instance_id**: The instance id of the cluster master node. (string)
- **emr_fleet.last_cluster.master.instance_type**: The ip of the cluster master node. (string)
- **emr_fleet.last_cluster.master.market**: The market of the cluster master node. (string)
- **emr_fleet.last_cluster.master.private_dns_name**: The private dns name of the cluster master node. (string)
- **emr_fleet.last_cluster.master.private_ip_address**: The private ip address of the cluster master node. (string)
- **emr_fleet.last_cluster.master.public_dns_name**: The public dns name of the cluster master node. (string)
- **emr_fleet.last_cluster.master.public_ip_address**: The public ip address of the cluster master node. (string)

## Configuration for `emr_fleet.shutdown_cluster>` operator

### Options

- **emr_fleet.shutdown_cluster>**: Specifies either the ID of an existing cluster (string, required)

## Configuration for `emr_fleet.detect_clusters>` operator

### Options

- **created_within**: Duration clusters created within. (`DurationParam`, required)
- **duration_after_created**: Duration after clusters are created. (`DurationParam`, default: **created_within**)
- **regexp**: Regular expression to filter listing clusters. (string, default: `".*"`)
- **states**: The cluster states filters to apply when listing clusters. Valid values are `"STARTING"`, `"BOOTSTRAPPING"`, `"RUNNING"`, `"WAITING"`, `"TERMINATING"`, `"TERMINATED"` and `"TERMINATED_WITH_ERRORS"`. (array of string, default: `["RUNNING", "WAITING"]`)

### Output parameters

- **emr_fleet.last_detection.is_detected**: Detected or not. (boolean)
- **emr_fleet.last_detection.clusters**:  Last detected clusters' information. (array of map)
  ```json
  [ 
      { 
          "id": "string",
          "name": "string",
          "normalized_instance_hours": number,
          "current_state": "string",
          "last_state_change_reason_code": "string",
          "last_state_change_reason_message":"string",
          "created_at": number,
          "end_at": number,
          "ready_at": number
      }
  ]
  ```

# Note

- Only emr-5.10.0 or larger releases are supported.
- There is no compatibility against [`emr>` operator](https://docs.digdag.io/operators/emr.html) because of the maintainability.

# Development

## Run an Example

### 1) build

```sh
./gradlew publish
```

Artifacts are build on local repos: `./build/repo`.

### 2) get your aws profile

```sh
aws configure
```

### 3) run an example

```sh
./example/run.sh
```

## (TODO) Run Tests

```sh
./gradlew test
```

# ChangeLog

[CHANGELOG.md](./CHANGELOG.md)

# License

[Apache License 2.0](./LICENSE.txt)

# Author

@civitaspo

