#!/bin/sh

DIR=$(dirname "$0")

cd $DIR/

echo "Now in directory $(pwd)"

if [[ $(git status -s) ]]
then
    echo "The working directory is dirty. Please commit any pending changes."
    exit 1;
fi

echo "Cleaning site directory"
rm -rf site
mkdir site
git worktree prune
rm -rf .git/worktrees/site/

echo "Checking out site branch into site/"
git worktree add -B site site origin/site || exit 1

echo "Done!"
