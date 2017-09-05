#!/bin/sh
#
# Executes jdeps on a series of jars to create one or more dot file(s) and
# prints the concatenation of these dot file(s).

set -e

readonly JDEPS_BIN=$(mktemp -d)

# Delete temp directory on exit.
trap "rm -rf $JDEPS_BIN" EXIT

jdeps -verbose:class -filter:none -dotoutput "$JDEPS_BIN" "$@"

# Given a series of jars, jdeps produces a dot file for each jar and an
# additional summary dot file. To ensure completeness of our class dependency
# graph, we concatenate each non-summary dot file. The summary file has no
# value for us.
rm "$JDEPS_BIN"/summary.dot
cat "$JDEPS_BIN"/*.dot
