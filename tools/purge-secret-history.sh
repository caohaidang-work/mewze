#!/usr/bin/env bash
# Removes keystore.properties from the ENTIRE git history (all branches and tags) of a repository,
# working on a FRESH clone so your current working copy is never touched.
#
#   tools/purge-secret-history.sh https://github.com/caohaidang-work/mewze.git [file-to-purge]
#
# Needs: git, python3, git-filter-repo   (pip install git-filter-repo)
# ROTATE THE PASSWORD FIRST (tools/rotate-keystore-password.sh): history rewriting does not make a
# leaked secret safe, it only stops it being easy to find.
set -euo pipefail

URL="${1:?usage: $0 <repo-url> [path-to-purge]}"
TARGET="${2:-keystore.properties}"
WORK="$(mktemp -d)/clean"

command -v git-filter-repo >/dev/null 2>&1 || { echo "Install it with: pip install git-filter-repo" >&2; exit 1; }

echo ">> Fresh clone -> $WORK"
git clone --no-local "$URL" "$WORK"
cd "$WORK"
# Make sure every remote branch exists locally so filter-repo rewrites them all.
for b in $(git branch -r | grep -v -- '->' | sed 's#origin/##'); do git branch --track "$b" "origin/$b" 2>/dev/null || true; done

BEFORE="$(git rev-list --all --count)"
echo ">> Removing '$TARGET' from $BEFORE commits (branches + tags)..."
git filter-repo --force --invert-paths --path "$TARGET"

echo ">> Verifying"
LEFT="$(git log --all --oneline -- "$TARGET" | wc -l | tr -d ' ')"
[ "$LEFT" = "0" ] || { echo "Still present in $LEFT commits - aborting." >&2; exit 1; }
echo "   commits now: $(git rev-list --all --count)   tags: $(git tag | wc -l | tr -d ' ')   '$TARGET' in history: 0"

cat <<MSG

Clean copy is in: $WORK

BEFORE pushing:
  * GitHub -> Actions -> "Release APK" -> ... -> Disable workflow.
    (Force-pushing tags v* would otherwise re-run the release build with your secrets.)
  * Be sure the password has been rotated.

MSG
read -rp "Force-push the cleaned history to $URL now? Type YES to continue: " ANSWER
[ "$ANSWER" = "YES" ] || { echo "Not pushed. Review $WORK and push manually if you want."; exit 0; }

git remote add origin "$URL"
git push --force --all origin
git push --force --tags origin

cat <<MSG

Pushed. Remaining steps:
  1. Re-enable the "Release APK" workflow.
  2. Delete your OLD local clone and re-clone (old objects still contain the file):
       git clone $URL
  3. Old commit hashes can stay reachable on GitHub via direct SHA links, forks and caches.
     Ask GitHub Support to run a gc / remove cached views (support.github.com -> "Remove sensitive data"),
     and tell anyone who forked the repo. That is why rotating the password matters most.
MSG
