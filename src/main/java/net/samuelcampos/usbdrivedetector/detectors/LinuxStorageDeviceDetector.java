/*
 * Copyright 2014 samuelcampos.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.samuelcampos.usbdrivedetector.detectors;

import lombok.extern.slf4j.Slf4j;
import net.samuelcampos.usbdrivedetector.USBStorageDevice;
import net.samuelcampos.usbdrivedetector.process.CommandExecutor;
import net.samuelcampos.usbdrivedetector.process.OutputProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tested on Linux Ubuntu 13.10
 *
 * @author samuelcampos
 */
@Slf4j
public class LinuxStorageDeviceDetector extends AbstractStorageDeviceDetector {

    private static final String CMD_DF = "df -h";
    private static final Pattern command1Pattern = Pattern.compile("^(\\/[^ ]+)[^%]+%[ ]+(.+)$");

    private static final String CMD_CHECK_USB = "udevadm info -q property -n ";

    private static final String INFO_BUS = "ID_BUS";
    private static final String INFO_USB = "usb";
    private static final String INFO_NAME = "ID_FS_LABEL";
    private static final String INFO_UUID = "ID_FS_UUID";

    private static final String DISK_PREFIX = "/dev/";

    protected LinuxStorageDeviceDetector(final CommandExecutor commandExecutor) {
        super(commandExecutor);
    }

    private void readDiskInfo(final DiskInfo disk) {

        final String command = CMD_CHECK_USB + disk.getDevice();

        try (final OutputProcessor commandOutputProcessor = commandExecutor.executeCommand(command)) {

            commandOutputProcessor.processOutput(outputLine -> {

                final String[] parts = outputLine.split("=");

                if(parts.length > 1){
                    switch (parts[0].trim()) {
                        case INFO_BUS:
                            disk.setUSB(INFO_USB.equals(parts[1].trim()));
                            break;
                        case INFO_NAME:
                            disk.setName(parts[1].trim());
                            break;
                        case INFO_UUID:
                            disk.setUuid(parts[1].trim());
                            break;
                    }
                }

            });

        } catch (final IOException e) {
            log.error(e.getMessage(), e);
        }

    }


    @Override
    public List<USBStorageDevice> getStorageDevices() {
        final ArrayList<USBStorageDevice> listDevices = new ArrayList<>();

        try (final OutputProcessor commandOutputProcessor = commandExecutor.executeCommand(CMD_DF)){
            commandOutputProcessor.processOutput((String outputLine) -> {
                final Matcher matcher = command1Pattern.matcher(outputLine);

                if (matcher.matches()) {

                    // device name, like /dev/sdh1
                    final String device = matcher.group(1);

                    // mount point, like /media/usb
                    final String rootPath = matcher.group(2);

                    if(device.startsWith(DISK_PREFIX)){
                        final DiskInfo disk = new DiskInfo(device);
                        disk.setMountPoint(rootPath);
                        readDiskInfo(disk);

                        if(disk.isUSB()){
                            getUSBDevice(disk.getMountPoint(), disk.getName(), disk.getDevice(), disk.getUuid())
                                    .ifPresent(listDevices::add);
                        }
                    }
                }
            });

        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

        return listDevices;
    }
}
