#!/usr/bin/env bash

# Exit script if you try to use an uninitialized variable.
set -o nounset

# Exit script if a statement returns a non-true return value.
set -o errexit

# Use the error status of the first failure, rather than that of the last item in a pipeline.
set -o pipefail

function current_branch {
    branch_name="$(git symbolic-ref HEAD 2>/dev/null)" ||
    branch_name="(unnamed branch)"     # detached HEAD
    branch_name=${branch_name##refs/heads/}
    echo "$branch_name"
}

branch=$(current_branch)
upper_branch=$(echo $branch | awk '{print toupper($0)}')
regex_check="^(RELEASE)|(HOTFIX).*$"
regex_valid="^(release)|(hotfix).*$"

if [[ "$upper_branch" =~ $regex_check ]] && [[ ! "$branch" =~ $regex_valid ]]; then
  echo
  echo " You are trying to commit on the *${branch}* branch."
  echo " To play well with CircleCI, you should name your branch with one of the following patterns. Case sensitive."
  echo
  echo "     'release-X.X.X'"
  echo "     'hotfix-X.X.X'"
  echo
  echo " If you really want to do this, force the commit by adding --no-verify to the command."
  echo " Note that this won't build properly on CircleCI."

  exit 1
fi
