# Bintray Backuper

A tool to backup any type of public/private repositories from Bintray.

## Table of Contents

- [Reasons to back up repositories from Bintray](#reasons-to-back-up-repositories-from-bintray)
- [Key Features](#key-features)
- [Usage](#usage)
- [Releases](#releases)

## Reasons to Back up Repositories from Bintray

- Migration to another artifact hosting service
- Protection from [Bintray downtimes](https://status.bintray.com)
- Prevention of data loss in case Bintray loses it

Backups, aka "better safe than sorry"!

## Key Features

- Parallel downloads
- Parallel metadata resolution
- Download is skipped if checksum of local file matches expected one from Bintray metadata
- Checksum is verified for freshly downloaded file to prevent data corruption
- Timeouts, threads and buffers can be configured for better performance

## Usage

`bintray-backuper` is [released](#releases) in form of a runnable `.jar` file, it requires JVM 1.8+ to run:

```console
java -jar bintray-backuper.jar --subject myorg --download-dir backup-dir
```

### Required Arguments

- `--subject`
    - Bintray "subject", either org or user name that hosts files.
    - Example: `--subject my-org`
- `--download-dir`
    - Directory to download files to. Files will be stored in following layout: 'download-dir/subject/repo/path-to-file'. Directory layout will be created if it doesn't exist. Checksum of existing files will be verified against metadata downloaded from Bintray.
    - Example: `--download-dir backup-dir`


### Optional Environment Variables

- `BINTRAY_BACKUPER_API_CREDENTIALS`
    - Bintray API credentials in form of `user:apikey` string
    - To obtain API key [go to "Edit Your Profile" on Bintray](https://bintray.com/profile/edit) → "API Key"
    - Default: "" (no value, requests are made anonymously and are subject to Bintray anti-DDoS and other checks)
    - Example: `BINTRAY_BACKUPER_API_CREDENTIALS="myuser:myapikey" java -jar bintray-backuper.jar --subject myorg`

### Optional Arguments

- `--http-connection-timeout`
    - HTTP connection timeout (seconds)
    - Default: `30` (seconds)
    - Example: `--http-connection-timeout 10`
- `--http-write-timeout`
    - HTTP write timeout for individual socket write (seconds)
    - Default: `60` (seconds)
    - Example: `--http-write-timeout 10`
- `--http-read-timeout`
    - HTTP read timeout for individual socket read (seconds)
    - Default: `60` (seconds)
    - Example: `--http-read-timeout 10`
- `--http-call-timeout`
    - HTTP call timeout (seconds)
    - Default: `300` (seconds)
    - Example: `--http-call-timeout 600`
- `--network-buffer-bytes`
    - Network stream buffer
    - Default: `16384` (bytes)
    - Example: `--network-buffer-bytes 32768`
- `--http-threads`
    - Number of threads for HTTP requests
    - Default: `6` (threads)
    - Example: `--http-threads 8`
- `--checksum-threads`
    - Number of threads for checksum verification
    - Default: number of cores * 6 (disk bound)
    - Example: `--checksum-threads 32`
- `--checksum-buffer-bytes`
    - Checksum disk stream buffer
    - Default: `16384` (bytes)
    - Example: `--checksum-buffer-bytes 65536`
- `--download-retries`
    - Number of retries to attempt for each download
    - Default: `3` (attempts)
    - Example: `--download-retries 5`
- `--api-endpoint`
    - Bintray-compatible API endpoint to use
    - Default: `https://api.bintray.com/`
    - Example: `--api-endpoint https://mycompany-bintray-proxy.com/`
- `--downloads-endpoint`
    - Bintray-compatible downloads endpoint to use
    - Default: `https://dl.bintray.com/`
    - Example: `--downloads-endpoint https://dl.mycompany-bintray-proxy.com/`

## Releases

TODO

