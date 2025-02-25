# Releases

Thunderbird for Android follows a release train model to ensure timely and predictable releases. This model allows for regular feature rollouts, stability improvements, and bug fixes.

## Branches in the Release Train Model

### Daily

Daily builds are used for initial testing of new features and changes. Feature flags are additionally used to work on features that are not yet ready for consumption.

- **Branch:** `main`
- **Purpose:** Active development of new features and improvements
- **Release Cadence:** Daily
- **Audience:** Developers and highly technical users who want to test the bleeding edge of Thunderbird. Daily builds are unstable and not recommended for production use.
- **Availability:** Daily builds are available on the Play Store internal channel. APKs are available on [ftp.mozilla.org](https://ftp.mozilla.org/pub/thunderbird-mobile/).

### Beta

After features are stabilized in Daily, they are merged into the Beta branch for broader testing. Uplifts are limited to bug/security fixes only. The Beta branch serves as a preview of what will be included in the next stable release, allowing for user feedback and final adjustments before general availability.

- **Branch:** `beta`
- **Purpose:** Pre-release testing
- **Release Cadence:** Weekly with the option to skip if not needed
- **Merge Cadence:** Every 2 Months
- **Audience:** Early adopters and testers. Testers are encouraged to provide error logs and help reproduce issues filed.
- **Availability:** Beta builds are available from the [Play Store](https://play.google.com/store/apps/details?id=net.thunderbird.android.beta) and [F-Droid](https://f-droid.org/packages/net.thunderbird.android.beta).

### Release

This branch represents the stable version of Thunderbird, which is released to the public. It is tested and suitable for general use. Bug fixes and minor updates are periodically applied between major releases. Uplifts to Release are limited to stability/security fixes only.

- **Branch:** `release`
- **Purpose:** Stable releases
- **Release Cadence:** Major releases every 2 months. Minor releases every 2 weeks with the option to skip if not needed.
- **Merge Cadence:** Every 2 months
- **Audience:** General users. Users may be filing bug reports or leaving reviews to express their level of satisfaction.
- **Availability:** Release builds are available from the [Play Store](https://play.google.com/store/apps/details?id=net.thunderbird.android) and [F-Droid](https://f-droid.org/packages/net.thunderbird.android).

## Example Feature Release Flow

1. A new feature is developed and merged via pull requests into the `main` branch.
2. Every 2 months, `main` is merged into the `beta` branch for external testing and feedback.
3. Every 2 months, `beta` is merged into the `release` branch, and a release is made available to all users.

## Example Bug Release Flow

1. A high-impact bug is fixed and merged via pull request into the `main` branch.
2. After it has received adequate testing on `daily`, the fix is cherry-picked (uplifted) to the `beta` branch and released in the next scheduled beta.
3. After it has received adequate testing on `beta`, the fix is cherry-picked (uplifted) to the `release` branch and released in the next stable minor release.

## Sample Release Timeline

| Milestone                      | Details   | Date   |
| ------------------------------ | --------- | ------ |
| TfA 11.0a1 starts              |           | Feb 28 |
| TfA merge 11.0a1 main->beta    |           | May 2  |
| TfA 11.0b1                     |           | May 5  |
| TfA 11.0bX                     | If needed | May 12 |
| TfA 11.0bX                     | If needed | May 19 |
| TfA 11.0bX                     | If needed | May 26 |
| TfA 11.0bX                     | If needed | Jun 2  |
| TfA 11.0bX                     | If needed | Jun 9  |
| TfA 11.0bX                     | If needed | Jun 16 |
| TfA 11.0bX                     | If needed | Jun 23 |
| TfA 11.0bX                     | If needed | Jun 30 |
| TfA merge 11.0bX beta->release |           | Jun 30 |
| TfA 11.0                       |           | Jul 7  |
| TfA 11.X                       | If needed | Jul 21 |
| TfA 11.X                       | If needed | Aug 4  |
| TfA 11.X                       | If needed | Aug 18 |
| TfA 11.X                       | If needed | Sep 1  |

## Feature Flags

Thunderbird for Android uses Feature Flags to disable features not yet ready for consumption.

- On `main`, feature flags are enabled as soon as developers have completed all pull requests related to the feature.
- On `beta`, feature flags remain enabled unless the feature has not been fully completed and the developers would like to pause the feature.
- On `release`, feature flags are disabled until an explicit decision has been made to enable the feature for all users.

## Versioning System

### Version Names

Thunderbird for Android stable release versions follow the `X.Y` format, where:

- **X (Major version):** Incremented for each new release cycle.
- **Y (Patch version):** Incremented when changes are added to an existing major version.

For beta builds, the suffix `b1` is appended, where the number increments for each beta. For daily builds, the suffix `a1` is appended, which remains constant.

### Version Codes

The version code is an internal version number for Android that helps determine whether one version is more recent than another.

The version code for beta and release is an integer value that increments for each new release.

The version code for daily is calculated based on the date and has the format `yDDDHHmm`:

- **y**: The number of years since a base year, with 2023 as the starting point (e.g., 2024 is 1)
- **DDD**: The day of the year in 3 digits, zero-padded
- **HH**: The hour of the day in 2 digits (00–23)
- **mm**: The minute of the hour in 2 digits

For example:

- `2024-02-09 16:45` → `1 | 040 | 16 | 45` → `10401645`
- `2025-10-12 09:23` → `2 | 285 | 09 | 23` → `22850923`
- `2122-02-09 16:45` → `99 | 040 | 16 | 45` → `990401645`

## Merge Days

Active development occurs on the `main` branch and becomes part of daily. Every 2 months:

1. `main` is merged into `beta`, for testing.
2. `beta` is merged into `release`, making it publicly available.

On the former, `main` carries over to `beta`, where the community can test the changes as part of “Thunderbird Beta for Testers” (`net.thunderbird.android.beta`) until the next merge day.
On the latter, code that was in beta goes to release, where the general population receives product updates (`net.thunderbird.android`).

When a merge occurs, the version name is carried forward to the next branch. However, the alpha and beta suffixes are removed/reset accordingly. For example, let’s say we are shortly before the Thunderbird 9.0 release. The latest releases were Thunderbird 8.2, Thunderbird Beta 9.0b4, and Thunderbird Daily 10.0a1. Here is what happens:

- The `beta` branch is merged to `release`. The resulting version on release changes from 8.2 to 9.0.
- The `main` branch is merged to `beta`. The resulting version on beta changes from 9.0b4 to 10.0b1
- The `main` branch version number is changed from 10.0a1 to 11.0a1

While the version name changes, it must be ensured that the version code stays the same for each branch. Our application IDs are specific to the branch they are on. For example:

- Beta always uses `net.thunderbird.android.beta` as the app ID. Let's say the version code is 20 at 9.0b4, it will be 21 at 10.0b1.
- Likewise, when 9.0b4 becomes 9.0, if the version code on beta is 20 and on release it is 12, then 9.0 becomes 13 and not 21.

## Merge Process

Merges are performed with the `git merge` command:

```sh
 git checkout beta
 git merge main
```

This approach enables various benefits, including:

- Carrying forward main branch history to beta, and beta branch history to release.
- No branch history is lost.
- Git tags are retained in git log.
- Files/code that is unique per branch can remain that way (e.g. notes files such as changelog_master.xml, version codes).

## Branch Uplifts

If the urgency of a fix requires it to be included in the Beta or Release channel before the next merge, the uplift process is followed. If possible, uplifts should be avoided and patches should “ride the train” instead, following the merge day cycle.

### Uplift Criteria

Beta uplifts should:

- Be limited to bug/security fixes only (features ride the train).
- Not change any localizable strings
- Have tests, or a strong statement of what can be done in the absence of tests.
- Have landed in main and stabilized on the daily channel.
- Have a comment in the GitHub issue assessing performance impact, risk, and reasons the patch is needed on beta.

Release uplifts should additionally:

- Be limited to stability/security fixes only (features ride the train).
- Have landed in beta and stabilized on the beta channel.

### Uplift Process

1. The requestor adds the "task: uplift to beta" or "task: uplift to release" label to a merged pull request.
2. The requestor makes a comment in the associated issue with the Approval Request Comment template filled out.
3. The release driver reviews all uplift requests and, retaining the label for approved uplifts and removing the label for rejected uplifts.
4. The release driver runs the Uplift Merges action for the specified target branch, which will remove the label, cherry-pick the commits, and push to the target branch.

Uplift patches are generated with:

```sh
git cherry-pick -x <commit-hash>
```

Template for uplift requests:

```sh
[Approval Request Comment]
Original Issue/Pull request:
Regression caused by (issue #):
User impact if declined:
Testing completed (on daily, etc.):
Risk to taking this patch (and alternatives if risky):
```

## Releases

Releases for both K-9 and Thunderbird for Android are automated with github actions.
Daily builds are scheduled with the [Daily Builds](https://github.com/thunderbird/thunderbird-android/actions/workflows/daily_builds.yml) action and all builds are performed by the [Shippable Build & Signing](https://github.com/thunderbird/thunderbird-android/actions/workflows/shippable_builds.yml) action.

For the historical manual release process, see [Releasing](RELEASING.md).

### Release Process

These are the general steps for a release:

1. Perform merge or uplifts. Each release is the result of either a merge or uplift.
2. Draft release notes at [thunderbird-notes](https://github.com/thunderbird/thunderbird-notes).
3. Trigger build via the [Shippable Build & Signing](https://github.com/thunderbird/thunderbird-android/actions/workflows/shippable_builds.yml) action.
4. Review the build results by reviewing the action summary and the git commits resulting from the build.
5. Smoke test the build.
6. Promote TfA and K-9 releases in Play Store.
7. Wait for Play Store review to complete.
8. Update F-Droid to new TfA and K-9 releases.
9. Send notification to Matrix channels, and beta or planning mailing lists.
