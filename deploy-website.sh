#!/bin/sh

DIR=$(dirname "$0")

cd $DIR/

if [[ $(git status -s) ]]
then
    echo "The working directory is dirty. Please commit any pending changes."
    exit 1;
fi

echo "Deleting old publication"
rm -rf public
mkdir public
git worktree prune
rm -rf .git/worktrees/public/

echo "Checking out gh-pages branch into public"
git worktree add -B gh-pages public origin/gh-pages

echo "Removing existing files"
rm -rf public/*
rm -rf site/target

echo "Generating site"
cd site/
magnanimous -style=solarized-dark -globalctx=_github_global_context || exit 1
cd ..

mv site/target/* public/

if [ "$1" = "-d" ]
then
    echo "Updating gh-pages branch and deploying website"
    cd public && git add --all && git commit -m "Publishing to gh-pages" && git push
fi
