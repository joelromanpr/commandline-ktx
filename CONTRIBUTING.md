# Contributing to commandline-ktx

Thanks for helping improve the Kotlin parser. Bug reports, documentation fixes, tests, and focused features are welcome. Please follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Branch and pull request flow

`main` is the default integration branch. Everyone uses the same short-lived topic-branch flow; there is no separate `develop` branch.

1. Start from the latest `main`. Fork the repository if you do not have write access; maintainers can create a branch in this repository.
2. Create a descriptive branch such as `fix/negative-values`, `feat/help-output`, or `docs/contributing`. A personal prefix is fine; branch names are not an eligibility rule.
3. Make one focused change. Add or update tests for parser behavior and update the README or KDoc when public behavior changes.
4. Run `./gradlew spotlessCheck build` locally. To apply the existing Spotless formatting first, run `./scripts/prepare_for_pr.sh -y`, inspect its diff, then rerun the checks.
5. Open a pull request **into `main`** using the template. Include the reason for the change, the behavior you tested, and any API or release impact. Draft pull requests are welcome for early feedback.
6. The verification workflow runs on the pull request. A maintainer reviews it, resolves feedback, and merges it when checks and branch rules are satisfied. Prefer a squash merge so each PR becomes one clear change on `main`.

External contributors can keep a fork current with `git fetch upstream` and branch from `upstream/main`. Maintainers should also branch from current `main` and use a pull request, including for their own changes. Delete a topic branch after its pull request is merged.

## Local development

The Gradle wrapper supplies Gradle 8.14, and the modules use a Java 17 toolchain. The repository has two modules: `library` contains the published parser and tests, while `demo` shows an application using the library.

```bash
./gradlew spotlessCheck build
./gradlew :library:test
./gradlew :demo:run --args='--help'
```

`./scripts/prepare_for_pr.sh --check -y` checks Spotless without editing files. Its default mode applies formatting. Keep generated files, local credentials, and unrelated worktree changes out of the pull request.

For architecture and known limitations, see [Project map](docs/PROJECT_MAP.md). The most valuable parser changes include tests for the current option-group behavior, default precedence, negative values, and extra positional arguments.

## Releases

Only maintainers publish. Contributors can note release impact in a pull request; they need no publishing credentials. Merging a pull request never publishes a package.

Release when the intended changes are merged and documented, and `verify` passes on `main`. Use a patch version for compatible fixes, minor for compatible additions, and major for breaking API changes.

1. In a pull request, set `library/build.gradle.kts` to `X.Y.Z` without `-SNAPSHOT` and update the README dependency examples. Merge after `verify` passes.
2. From a clean checkout of that merged `main` commit, create and push its tag:

   ```bash
   git fetch origin
   git switch main
   git pull --ff-only origin main
   git tag -a vX.Y.Z -m "Release X.Y.Z"
   git push origin vX.Y.Z
   ```

3. Pushing the tag starts `release.yml` immediately. It checks the tag and version, builds, and publishes to Maven Central. Confirm the workflow succeeds and the artifact is available; then use another pull request to move the library to the next `-SNAPSHOT` version.

Conventional Commit messages are welcome (`feat:`, `fix:`, `docs:`, `test:`), but the pull request title and description matter more than a particular commit format.
