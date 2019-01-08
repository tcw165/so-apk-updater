#!/usr/bin/env bash

COMMIT_FILE=$1
COMMIT_MSG=$(cat "$1")
CURRENT_BRANCH=$(git rev-parse --abbrev-ref HEAD)
USING_GITHUB=true
USING_JIRA=false
JIRA_ID=$(printf "%s" "$CURRENT_BRANCH" | grep -Eo "[a-zA-Z0-9]{1,10}-?[a-zA-Z0-9]+-\d+")
GITHUB_ID=$(echo $JIRA_ID | awk -F'-' '{printf "%s", $2}')

# If commit message is a fixup message, ignore it
if printf "%s" "$COMMIT_MSG" | grep 'fixup!'; then
    FIXUP="YES"
fi

TEXT_TO_APPEND=""

if [[ "$USING_GITHUB" = true ]] ; then
    # If the commit message already has a Github Issue ID, ignore it
    if printf "%s" "$COMMIT_MSG" | grep "${GITHUB_ID}"; then
        HAS_GITHUB_ISSUE="YES"
    fi

    if [[ ! -z "$GITHUB_ID" ]] && [[ -z "${FIXUP}" ]] && [[ -z "${HAS_GITHUB_ISSUE}" ]]; then
        TEXT_TO_APPEND=$(printf "%s\nGithub: Fixes #%s" "$TEXT_TO_APPEND" "$GITHUB_ID")
        printf "Github ID #%s matched in current branch name, appending to commit message. (Use --no-verify to skip)\n" "$GITHUB_ID"
    fi
fi

if [[ "$USING_JIRA" = true ]] ; then
    # If the commit message already has an issue id, ignore it
    if printf "%s" "$COMMIT_MSG" | grep "${JIRA_ID}"; then
        HAS_JIRA_ISSUE="YES"
    fi

    if [[ ! -z "$JIRA_ID" ]] && [[ -z "${FIXUP}" ]] && [[ -z "${HAS_JIRA_ISSUE}" ]]; then
        TEXT_TO_APPEND=$(printf "%s\nJira: %s" "$TEXT_TO_APPEND" "$JIRA_ID")
        printf "Jira ID %s matched in current branch name, appending to commit message. (Use --no-verify to skip)\n" "$JIRA_ID"
    fi
fi

printf "%s\n\n%s" "$COMMIT_MSG" "$TEXT_TO_APPEND" > "$COMMIT_FILE"