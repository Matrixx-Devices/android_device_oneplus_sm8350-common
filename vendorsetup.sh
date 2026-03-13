#!/bin/bash

# Define paths
PATCH_DIR="device/oneplus/sm8350-common/patches"
GAMESPACE_DIR="packages/apps/GameSpace"

# Apply GameSpace live overlay sync patch if not already applied
if [ -d "$GAMESPACE_DIR" ]; then
    echo "Checking GameSpace patches..."
    cd $GAMESPACE_DIR
    
    # Check if the patch is already applied
    git diff --quiet app/src/main/java/io/chaldeaprjkt/gamespace/utils/GameModeUtils.kt
    
    # If the file hasn't been modified yet, try to patch it
    if [ $? -eq 0 ]; then
        echo "Applying GameSpace sync patch..."
        git apply ../../../$PATCH_DIR/gamespace_sync.patch >/dev/null 2>&1
        
        # Commit the patch so we know it's applied
        if [ $? -eq 0 ]; then
             git add app/src/main/java/io/chaldeaprjkt/gamespace/utils/GameModeUtils.kt
             git commit -m "gamespace-sync: Broadcast mid-game overlay changes to PowerTools"
        else
             echo "Warning: Could not apply GameSpace patch. Maybe already applied."
        fi
    else
        echo "GameSpace sync patch already active."
    fi
    
    cd ../../../
fi
