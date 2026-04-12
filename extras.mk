# DeviceAsWebcam
TARGET_BUILD_DEVICE_AS_WEBCAM := true

# Torch
$(call soong_config_set,libcameraservice,ext_lib,//$(LOCAL_PATH):libcameraservice_extension.oneplus_sm8350)

# OnePlus OOS Camera
$(call inherit-product-if-exists, vendor/oplus/camera/opluscamera.mk)

# Dolby 
$(call inherit-product, vendor/sony/dolby/sonydolby.mk)

# System properties
PRODUCT_SYSTEM_PROPERTIES += \
    pm.sleep_mode=1 \
    ro.iorapd.enable=false \
    iorapd.perfetto.enable=false \

PRODUCT_VENDOR_PROPERTIES += \
    vendor.post_boot.parsed=1

