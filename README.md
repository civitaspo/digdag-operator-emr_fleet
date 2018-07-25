# digdag-operator-emr_fleet
[![Digdag](https://img.shields.io/badge/digdag-v0.9.27-brightgreen.svg)](https://github.com/treasure-data/digdag/releases/tag/v0.9.27)

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
      - pro.civitaspo.digdag.plugin:digdag-operator-emr_fleet:0.0.1

+task1:
  emr_fleet.create_cluster>:
    name: my-cluster
    master:
      candidates:
        - instance_type: r4.xlarge
        - instance_type: m5.xlarge
    core:
      target_capacity: 128
      candidates:
        - instance_type: r4.8xlarge
          spot_units: 32
        - instance_type: r4.16xlarge
          spot_units: 64
        - instance_type: r4.4xlarge
          spot_units: 16
    release_label: emr-5.16.0
    applications:
      - Hadoop
      - Spark
      - Livy

+task2:
  emr_fleet.shutdown_cluster>: ${emr_fleet.last_cluster.id}

```

# Configuration

## Remarks

- An EMR Cluster is also called an job flow. In the below explanations, I use both terms.

## Common Configuration

### Secrets

- **emr_fleet.access_key_id**, **aws.access_key_id**: The AWS Access Key ID to use when submitting EMR jobs. (optional)
- **emr_fleet.secret_access_key**, **aws.secret_access_key**: The AWS Secret Access Key to use when submitting EMR jobs. (optional)
- **emr_fleet.role_arn**, **aws.role_arn**: The AWS Role to assume when submitting EMR jobs. (optional)
- **emr_fleet.region**, **aws.region**: The AWS region to use for EMR service. (optional)

### Options

- **auth_method**: name of mechanism to authenticate requests (`"basic"`, `"env"`, `"instance"`, `"profile"`, `"properties"`, `"anonymous"`, or `"session"`. default: `"basic"`)
  - `"basic"`: uses access_key_id and secret_access_key to authenticate.
    - **access_key_id**: AWS access key ID (string, default: **emr_fleet.access_key_id** or **aws.access_key_id**)
    - **secret_access_key**: AWS secret access key (string, default: **emr_fleet.secret_access_key** or **aws.secret_access_key**)
  - `"env"`: uses AWS_ACCESS_KEY_ID (or AWS_ACCESS_KEY) and AWS_SECRET_KEY (or AWS_SECRET_ACCESS_KEY) environment variables.
  - `"instance"`: uses EC2 instance profile.
  - `"profile"`: uses credentials written in a file. Format of the file is as following, where `[...]` is a name of profile.
    - **profile_file**: path to a profiles file. (string, default: given by AWS_CREDENTIAL_PROFILES_FILE environment varialbe, or ~/.aws/credentials).
    - **profile_name**: name of a profile. (string, default: `"default"`)
  - `"properties"`: uses aws.accessKeyId and aws.secretKey Java system properties.
  - `"anonymous"`: uses anonymous access. This auth method can access only public files.
  - `"session"`: uses temporary-generated access_key_id, secret_access_key and session_token.
    - **access_key_id**: AWS access key ID (string, default: **emr_fleet.access_key_id** or **aws.access_key_id**)
    - **secret_access_key**: AWS secret access key (string, default: **emr_fleet.secret_access_key** or **aws.secret_access_key**)
    - **session_token**: session token (string, required)
- **http_proxy** http proxy configuration to use when accessing AWS via http proxy. (optional)
  - **host** proxy host (string, required)
  - **port** proxy port (int, optional)
  - **https** use https or not (boolean, default true)
  - **user** proxy user (string, optional)
  - **password** proxy password (string, optional)
- **role_arn**: The AWS Role to assume when submitting EMR jobs. (string, optional)
- **region**: The AWS region to use for EMR service. (string, optional)
- **endpoint**: The AWS EMR endpoint address to use. (string, optional)


## Configuration for `emr_fleet.create_cluster>` operator

### Options

- **name**: The name of the job flow. (string, required)
- **tags**: A list of tags to associate with a cluster and propagate to Amazon EC2 instances. (array of map, optional)
- **release_label**: The Amazon EMR release label, which determines the version of open-source application packages installed on the cluster. Release labels are in the form emr-x.x.x, where x.x.x is an Amazon EMR release version. For more information about Amazon EMR release versions and included application versions and features, see [Docs](https://docs.aws.amazon.com/emr/latest/ReleaseGuide/). (string, default: `emr-5.16.0` )
- **custom_ami_id**: Available only in Amazon EMR version 5.7.0 and later. The ID of a custom Amazon EBS-backed Linux AMI. If specified, Amazon EMR uses this AMI when it launches cluster EC2 instances. (string, optional)
- **master_security_groups**: A list of Amazon EC2 security group IDs for the master node. The first one is used as Amazon EMR–managed security group, and the left is used as additional one. See [Docs](https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-security-groups.html) to get more information. (array of string, optional)
- **slave_security_groups**: A list of Amazon EC2 security group IDs for the slave node. The first one is used as Amazon EMR–managed security group, and the left is used as additional one. See [Docs](https://docs.aws.amazon.com/emr/latest/ManagementGuide/emr-security-groups.html) to get more information. (array of string, optional)
- **ssh_key**: The name of the EC2 key pair that can be used to ssh to the nodes. (string, optional)
- **subnet_ids**: Applies to clusters that use the instance fleet configuration. When multiple EC2 subnet IDs are specified, Amazon EMR evaluates them and launches instances in the optimal subnet. (array of string, optional)
- **availability_zones**: When multiple Availability Zones are specified, Amazon EMR evaluates them and launches instances in the optimal Availability Zone. (array of string, optional)
- **spot_specs**: The launch specification for Spot instances in the instance fleet, which determines the defined duration and provisioning timeout behavior. (map, default: `{}`)
  - **block_duration_minutes**: The defined duration for Spot instances (also known as Spot blocks) in minutes. When specified, the Spot instance does not terminate before the defined duration expires, and defined duration pricing for Spot instances applies. Valid values are 60, 120, 180, 240, 300, or 360. The duration period starts as soon as a Spot instance receives its instance ID. At the end of the duration, Amazon EC2 marks the Spot instance for termination and provides a Spot instance termination notice, which gives the instance a two-minute warning before it terminates. (integer, optional)
  - **timeout_action**: The action to take when **target_spot_capacity** has not been fulfilled when the **timeout_duration_minutes** has expired. Spot instances are not uprovisioned within the Spot provisioining timeout. Valid values are `TERMINATE_CLUSTER` and `SWITCH_TO_ON_DEMAND`. `SWITCH_TO_ON_DEMAND` specifies that if no Spot instances are available, On-Demand Instances should be provisioned to fulfill any remaining Spot capacity. (string, default: `TERMINATE_CLUSTER`)
  - **timeout_duration_minutes**: The spot provisioning timeout period in minutes. If Spot instances are not provisioned within this time period, the **timeout_action** is taken. Minimum value is 5 and maximum value is 1440. The timeout applies only during initial provisioning, when the cluster is first created. (integer, default: `45`)
- **master_fleet**: Describes the EC2 instances and instance configurations for master node that use the instance fleet configuration. (map, required)
  - **name**: The friendly name of the instance fleet. (string, default: `master instance fleet`)
  - **use_spot_instance**: Indicates whether master node uses Spot instance or On-Demand instance. (boolean, default: `true`)
  - **candidates**: An instance type configuration for each instance type in an instance fleet, which determines the EC2 instances Amazon EMR attempts to provision to fulfill On-Demand and Spot target capacities. There can be a maximum of 5 instance type configurations in a fleet. (array of map, required)
    - **bid_price**: The bid price for each EC2 Spot instance type as defined by **instance_type**. Expressed in USD. If neither **bid_price** nor **bid_percentage** is provided, **bid_percentage** defaults to 100%. (string, optional)
    - **bid_percentage**: The bid price, as a percentage of On-Demand price, for each EC2 Spot instance as defined by **instance_type**. Expressed as a number (for example, 20 specifies 20%). If neither **bid_price** nor **bid_percentage** is provided, **bid_percentage** defaults to 100%. (double, default: `100.0`)
    - **instance_type**: An EC2 instance type, such as `m3.xlarge`. (string, required)
    - **configurations**: A configuration classification that applies when provisioning cluster instances, which can include configurations for applications and software that run on the cluster. (array of map, optional)
      - **classification**: The classification within a configuration. (string, required)
      - **properties**: A set of properties specified within a configuration classification. (string to string map, default: optional)
      - **configurations**: A list of additional configurations to apply within a configuration object. (array of this configurations object, optional)
    - **ebs**: The Amazon EBS configuration of a cluster instance. (map, default: `{}`)
      - **optimized**: Indicates whether an Amazon EBS volume is EBS-optimized. (boolean, default: `true`)
      - **iops**: The number of I/O operations per second (IOPS) that the volume supports. (integer, default: `null`)
      - **size**: The volume size, in gibibytes (GiB). This can be a number from 1 - 1024. If the volume type is EBS-optimized, the minimum value is 10. (integer, default: `256`)
      - **type**: The volume type. Volume types supported are gp2, io1, standard. (string, default: `gp2`)
      - **volumes_per_instance**: Number of EBS volumes with a specific volume configuration that will be associated with every instance in the instance group. (integer, default: `1`)
- **core_fleet**: Describes the EC2 instances and instance configurations for core nodes that use the instance fleet configuration. (map, required)
  - **name**: The friendly name of the instance fleet. (string, default: `core instance fleet`)
  - **target_capacity**: The target capacity of **spot_units** for the instance fleet, which determines how many Spot instances to provision. When the instance fleet launches, Amazon EMR tries to provision Spot instances as specified by **candidates** settings. Each instance configuration has a specified **spot_units**. When a Spot instance is provisioned, the **spot_units** count toward the target capacity. Amazon EMR provisions instances until the target capacity is totally fulfilled, even if this results in an overage. (integer, required)
  - **candidates**: An instance type configuration for each instance type in an instance fleet, which determines the EC2 instances Amazon EMR attempts to provision to fulfill On-Demand and Spot target capacities. There can be a maximum of 5 instance type configurations in a fleet. (array of map, required)
    - **bid_price**: The bid price for each EC2 Spot instance type as defined by **instance_type**. Expressed in USD. If neither **bid_price** nor **bid_percentage** is provided, **bid_percentage** defaults to 100%. (string, optional)
    - **bid_percentage**: The bid price, as a percentage of On-Demand price, for each EC2 Spot instance as defined by **instance_type**. Expressed as a number (for example, 20 specifies 20%). If neither **bid_price** nor **bid_percentage** is provided, **bid_percentage** defaults to 100%. (double, default: `100.0`)
    - **instance_type**: An EC2 instance type, such as `m3.xlarge`. (string, required)
    - **configurations**: A configuration classification that applies when provisioning cluster instances, which can include configurations for applications and software that run on the cluster. (array of map, optional)
      - **classification**: The classification within a configuration. (string, required)
      - **properties**: A set of properties specified within a configuration classification. (string to string map, optional)
      - **configurations**: A list of additional configurations to apply within a configuration object. (array of this configurations object, optional)
    - **ebs**: The Amazon EBS configuration of a cluster instance. (map, default: `{}`)
      - **optimized**: Indicates whether an Amazon EBS volume is EBS-optimized. (boolean, default: `true`)
      - **iops**: The number of I/O operations per second (IOPS) that the volume supports. (integer, default: `null`)
      - **size**: The volume size, in gibibytes (GiB). This can be a number from 1 - 1024. If the volume type is EBS-optimized, the minimum value is 10. (integer, default: `256`)
      - **type**: The volume type. Volume types supported are gp2, io1, standard. (string, default: `gp2`)
      - **volumes_per_instance**: Number of EBS volumes with a specific volume configuration that will be associated with every instance in the instance group. (integer, default: `1`)
    - **spot_units**: The number of units that a provisioned instance of this type provides toward fulfilling the **target_capacity**. This value must be 1 or greater. (integer, default: `1`)
- **task_fleet**: Describes the EC2 instances and instance configurations for task nodes that use the instance fleet configuration. (map, optional)
  - **name**: The friendly name of the instance fleet. (string, default: `task instance fleet`)
  - The other options are the same as **task** option.
- **log_uri**: The location in Amazon S3 to write the log files of the job flow. If a value is not provided, logs are not created. (string, optional)
- **additional_info**: A JSON string for selecting additional features. (string, optional)
- **is_visible**: Whether the cluster is visible to all IAM users of the AWS account associated with the cluster. If this value is set to true, all IAM users of that AWS account can view and (if they have the proper policy permissions set) manage the cluster. If it is set to false, only the IAM user that created the cluster can view and manage it. (boolean, default: `true`)
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
  - **script**: The script run by the bootstrap action. (map, required)
    - **path**: Location of the script to run during a bootstrap action. Can be either a location in Amazon S3 or on a local file system. (string, required)
    - **args**: A list of command line arguments to pass to the bootstrap action script. (array of string, default: `[]`)
- **keep_alive_when_no_steps**: Specifies whether the cluster should remain available after completing all steps. (boolean, default: `true`)
- **termination_protected**: Specifies whether to lock the cluster to prevent the Amazon EC2 instances from being terminated by API call, user intervention, or in the event of a job-flow error. (boolean, default: `false`)

### Output parameters

- **emr_fleet.last_cluster.id**: The ID of the cluster created.

## Configuration for `emr_fleet.shutdown_cluster>` operator

### Options

- **cluster_id**: Specifies either the ID of an existing cluster (string, required)

# TODOs

- no Step Configurations
- no Kerberos Configurations

# Note

- Only emr-5.10.0 or larger releases are supported.
- There is no compatibility against [`emr>` operator](https://docs.digdag.io/operators/emr.html) because of the maintenancebility.

# Development

## Run an Example

### 1) build

```sh
./gradlew publish
```

Artifacts are build on local repos: `./build/repo`.

### 2) run an example

```sh
digdag selfupdate

digdag run --project sample plugin.dig -p repos=`pwd`/build/repo
```

You'll find the result of the task in `./sample/example.out`.

## Run Tests

```sh
./gradlew test
```

# ChangeLog

[CHANGELOG.md](./CHANGELOG.md)

# License

[Apache License 2.0](./LICENSE.txt)

# Author

@civitaspo

