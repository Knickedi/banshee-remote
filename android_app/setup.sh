#!/bin/bash

# This file is helping with project setup.
#
# Because this project is using the android-toolbox project and we don't want
# to reference the whole project (we don't need everything) we will create
# hard links to those files. This way improvements will be changed in the
# original project too when working on linux.
#
# If you just want to use this project as it is without messing with hard
# links that's fine too. It's just for developers who are willing to improve
# the android toolbox project and so a little helper script.


# PATHS (modify to fit your needs)

AT='/home/knickedi/Development/projects/android-toolbox'
BR='/home/knickedi/Development/projects/banshee-remote'


# PROJECT FILES (add if project references other android toolbox files)

FILES=(
	"content/NetworkStateBroadcast"
	"os/GeneralPool"
	"os/SoftPool"
	"util/AndroidUtils"
	"util/L"
	"util/StringUtils"
	"widget/HiddenQuickActionSetup"
	"widget/SwipeableHiddenView"
	"widget/SwipeableListItem"
	"widget/SwipeableListView"
)


# SCRIPT (don't modify)

P='/src/de/viktorreiser/toolbox'
ATP="${AT}/android_toolbox${P}"
BRP="${BR}/android_app${P}"

if [ ! -d $ATP ]; then
	echo "Path to android-toolbox repository is wrong!"
	return
elif [ ! -d $BRP ]; then
	echo "Path to banshee-remote repository is wrong!"
	return
fi

for f in ${FILES[@]}
do
	if [ -e "${ATP}/${f}.java" ]; then
		fName="${ATP}/${f}.java"
	else
		fName=""
	fi

	if [ -z $fName ]; then
		echo "File \"${f}\" not found in toolbox repository!"
	else
		rm -f "${BRP}/${f}.java"
		ln "${fName}" "${BRP}/${f}.java"
		echo "Create hard link for \"${f}\""
	fi
done

