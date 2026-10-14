# Integration Guide

## Branches

Each task or feature lives on its own branch `feature/<id>-<name>`. Integration branches collect completed features per phase before release.

## Pull request standard

- Links the task id and architecture references.
- Lists contracts used and any change requests.
- Includes test results and architecture check output.
- One feature per pull request.

## Automated checks

Build, unit tests, determinism test, architecture dependency test, formatting and static analysis. Golden file changes are flagged for explicit review.

## Merge conflict prevention

Module ownership; tasks limited to their modules; shared contracts change only via accepted change requests; frequent rebases onto the integration branch.

## Regression

Golden scenarios and replay checks run on every integration branch update.

## Phase gates

A phase closes when its deliverables, acceptance criteria and test requirements in IMPLEMENTATION_ROADMAP.md are met and the golden scenarios pass.
