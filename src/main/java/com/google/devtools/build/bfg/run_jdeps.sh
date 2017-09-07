#!/bin/sh
#
# Copyright 2017 The Bazel Authors. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
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
