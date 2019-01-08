#!/usr/bin/env bash

# Exit script if you try to use an uninitialized variable.
set -o nounset

# Exit script if a statement returns a non-true return value.
set -o errexit

# Use the error status of the first failure, rather than that of the last item in a pipeline.
set -o pipefail

ROOT_DIR="$(git rev-parse --show-toplevel)"
CURR_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
GIT_ROOT="$(git rev-parse --git-dir)"

echo "Setting up environment for $ROOT_DIR"

echo "Creating a symlink from $GIT_ROOT/hooks/pre-commit to enforceBranchName.sh" 1>&2
ln -s -f "$CURR_DIR/enforceBranchName.sh" "$GIT_ROOT/hooks/pre-commit"

echo "Creating a symlink from $GIT_ROOT/hooks/commit-msg to enforceTicketNumberInCommitMessage.sh" 1>&2
ln -s -f "$CURR_DIR/enforceTicketNumberInCommitMessage.sh" "$GIT_ROOT/hooks/commit-msg"

echo "Copying editorconfig to $ROOT_DIR/.editorconfig" 1>&2
cp "$CURR_DIR/editorconfig" "$ROOT_DIR/.editorconfig"