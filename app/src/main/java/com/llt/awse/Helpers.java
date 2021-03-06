/*
 * Copyright 2013 Bartosz Jankowski
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
package com.llt.awse;

import android.os.Environment;
import android.util.Log;

import com.spazedog.lib.rootfw3.RootFW;
import com.spazedog.lib.rootfw3.extenders.FileExtender;

import org.ini4j.Ini;
import org.ini4j.Profile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.IllegalFormatConversionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Helpers
{
    final private static String TAG = "AWSE";
    final private static Pattern regIniGpio = Pattern.compile("^port:(.*)<(.*)><(.*)><(.*)><(.*)>$");

    final private static Pattern regScriptGpio = Pattern.compile("^\\s*(\\w*)\\s*gpio.*\\(gpio:\\s*(.+), mul:\\s*(.+), pull\\s*(.+), drv\\s*(.+), data\\s*(.+)\\).*$");
    final private static Pattern regScriptString = Pattern.compile("^\\s*(\\w*)\\s*string.*(\".*\")$");
    final private static Pattern regScriptInt = Pattern.compile("^\\s*(\\w*)\\s*int\\s*(.*)$");
    final private static Pattern regScriptInvalid = Pattern.compile("^\\s*(\\w+)\\s*invalid\\s*(.*)$");


    // List of all known to me sections
    // Used to get config on boot 2.0 devices
    // Source: http://linux-sunxi.org/Fex_Guide
    public final static String[] mScriptSections = {"3g_para", "audio_para", "bt_para", "can_para", "card0_boot_para",
            "card2_boot_para", "card_boot0_para", "card_boot2_para", "card_boot", "card_burn_para", "clock",
            "compass_para", "cpus_config_paras", "csi0_para", "csi1_para", "ctp_list_para", "ctp_para", "disp_init",
            "dram_para", "dvfs_table", "dynamic", "emac_para", "fel_key", "g2d_para", "gmac_para", "gpio_init", "gpio_para",
            "gps_para", "gsensor_para", "gy_para", "hdmi_para", "i2s_para", "ir_para", "jtag_para", "lcd0_para", "lcd1_para",
            "leds_para", "locks_para", "ls_para", "mali_para", "mmc0_para", "mmc1_para", "mmc2_para", "mmc3_para", "motor_para",
            "ms_para", "msc_feature", "nand0_para", "nand1_para", "nand_para", "pcm_para", "platform", "pmu_para", "power_sply",
            "product", "ps2_0_para", "ps2_1_para", "recovery_key", "rtp_para", "sata_para", "sdio_wifi_para", "smc_para",
            "spdif_para", "spi0_para", "spi1_para", "spi2_para", "spi3_para", "spi_board0", "spi_devices", "system",
            "tabletkeys_para", "target", "tkey_para", "tv_out_dac_para", "tvin_para", "tvout_para", "twi0_para", "twi1_para",
            "twi2_para", "twi3_para", "twi_para", "uart_para0", "uart_para1", "uart_para2", "uart_para3", "uart_para4",
            "uart_para5", "uart_para6", "uart_para7", "uart_para", "usb_feature", "usb_wifi_para", "usbc0", "usbc1", "usbc2",
            "vip0_para", "vip1_para", "wifi_para"};

    /*
        Convert gpio abs val to name eg. 186 -> PM00
     */
    static String getGpioByNumber(int number)
    {
        final int[] Banks = {30, 10, 30, 30, 19, 8,  21, 33, 0,    0,    0,    11, 10};
        //                   PA, PB, PC, PD, PE, PF, PG, PH, [PI], [PJ], [PK], PL, PM

        int bank = 0;
        for(int i = 0; i < Banks.length; ++i)
        {
            if(number<bank + Banks[i])
            {
                int gpio = number - bank;
                char cBank = (char)(65+ i);
                return String.format("P%c%02d", cBank, gpio);
            }

            bank += Banks[i];
        }
        if(number> 202)
        {
            int gpio = number - 202;
            return String.format("power%d", gpio);
        }
        return "?";
    }

    /*
        Get section
     */
    static void getScriptSection(final RootFW root, final String section, Ini ini)
    {
        /*
        ++++++++++++++++++++++++++__sysfs_dump_mainkey++++++++++++++++++++++++++
    name:      wifi_para
    sub_key:   name           type      value
               ap6xxx_wl_regongpio      (gpio: 186, mul: 1, pull -1, drv -1, data 0)
               ap6xxx_wl_host_wakegpio      (gpio: 187, mul: 0, pull -1, drv -1, data 0)
               ap6xxx_bt_regongpio      (gpio: 189, mul: 1, pull -1, drv -1, data 0)
               ap6xxx_bt_wake gpio      (gpio: 139, mul: 1, pull -1, drv -1, data 0)
               ap6xxx_bt_host_wakegpio      (gpio: 188, mul: 0, pull -1, drv -1, data 0)
               wifi_power     string    "axp22_dldo1"
               wifi_used      int       1
               wifi_sdc_id    int       1
               wifi_usbc_id   int       1
               wifi_usbc_type int       1
               wifi_mod_sel   int       2
               ap6xxx_gpio_powerstring    "axp22_dldo2"
               ap6xxx_clk_powerstring    "axp22_dldo4"
--------------------------__sysfs_dump_mainkey--------------------------
         */
        FileExtender.File file = root.file("/sys/class/script/dump");

        file.write(section);
        FileExtender.FileData data = file.read();
        if(data.size() == 0)
            return;

        Profile.Section s = ini.add(section);
        for(String line : data.getArray())
        {
            Matcher m = regScriptInvalid.matcher(line);
            if(m.find())
            {
                s.add(m.group(1), "");
                continue;
            }
            m = regScriptGpio.matcher(line);
            if(m.find())
            {
                String gpio = String.format("port:%s<%s><%s><%s><%s>", getGpioByNumber(Integer.parseInt(m.group(2))),
                        (m.group(3).equals("-1") ? "default" : m.group(3)),  (m.group(4).equals("-1") ? "default" : m.group(4)),
                        (m.group(5).equals("-1") ? "default" : m.group(5)),  (m.group(6).equals("-1") ? "default" : m.group(6)));
                s.add(m.group(1), gpio);
                continue;
            }
            m = regScriptInt.matcher(line);
            if(m.find())
            {
                //Convert to hex
                try {
                    int hexInt = Integer.parseInt(m.group(2));
                    s.add(m.group(1), "0x" + Integer.toHexString(hexInt));
                } catch(NumberFormatException e)
                {
                    Log.e(TAG, "Failed to parse integer of " + m.group(1) + ", value to parse: " + m.group(2));
                    s.add(m.group(1), "0");
                }
                continue;
            }
            m = regScriptString.matcher(line);
            if(m.find())
            {
                s.add(m.group(1), m.group(2));
                continue;
            }

        }
    }

	static boolean isPortEntry(String val)
	{
		return regIniGpio.matcher(val).find();
	}
	
	static String[] getPortValues(String val)
	{
		Matcher m = regIniGpio.matcher(val);
		String[] ret = new String[5];
		if(m.find())
		{
			ret[0] = m.group(1);
			ret[1] = m.group(2);
			ret[2] = m.group(3);
			ret[3] = m.group(4);
			ret[4] = m.group(5);
			
		}
		return ret;
	}

    static void unmountLoader(final RootFW root)
    {
        if (root.filesystem("/dev/block/nanda").isMounted()) {
            Log.v(TAG, "Unmounting device...");
            if (root.filesystem("/dev/block/nanda").removeMount())
                //Use rmdir to prevent from important files removal!
                root.shell().run("rmdir /mnt/awse");
        }
        /*else if (root.filesystem("/dev/block/by-name/bootloader").isMounted()) {
            Log.v(TAG, "Unmounting device...");
            if (root.filesystem("/dev/block/nanda").removeMount())
                //Use rmdir to prevent from important files removal!
                root.shell().run("rmdir /mnt/awse");
        }*/
        else {
            Log.v(TAG, "Skipping unmount routine, cause loader isn't mounted");
        }
    }

    static void exportIni(final Ini ini, String name, final File path) throws IOException {
        File f = null;

        if(name.toLowerCase().endsWith(".fex"))
        {
            name = name.substring(0, name.length() - 4);
        }

        try {
            f = new File(path, name + ".fex");
            if(!f.exists())
                f.createNewFile();
        }
        catch(IOException e)
        {
            Log.e(TAG, "Failed to create file '" + path + "/" + name + ".fex :" + e);
            throw e;
        }
        finally {
                try {
                    ini.store(f);
                } catch (IOException e) {
                    f.delete();
                    Log.e(TAG, "Failed to write ini: " + e);
                    throw e;
                }
        }
        Log.v(TAG, "Succesfully stored FEX to: " + path + "/" + name + ".fex :");
    }

    static void exportBin(final Ini ini, String name, final File path) throws IOException {
        File f = null;

        if(name.toLowerCase().endsWith(".bin"))
        {
            name = name.substring(0, name.length() - 4);
        }

        try {
            f = new File(path, name + ".bin");
            if(!f.exists())
                f.createNewFile();
        }
        catch(IOException e)
        {
            Log.e(TAG, "Failed to create file '" + path + "/" + name + ".fex :" + e);
            throw e;
        }
        finally {

            byte[] output = FexUtils.compileFex(ini.toString().toCharArray(), ini.toString().getBytes().length);
            if(output == null)
            {
                throw new RuntimeException("Fex compilation failed");
            }
            try {
                FileOutputStream fis = new FileOutputStream(f);
                fis.write(output);
                fis.flush();
                fis.close();
            } catch (IOException e) {
                f.delete();
                Log.e(TAG, "Failed to write bin: " + e);
                throw e;
            }
        }
        Log.v(TAG, "Succesfully stored FEX to: " + path + "/" + name + ".fex :");
    }


    static void removeTempFiles()
    {
        java.io.File f = new java.io.File(Environment.getExternalStorageDirectory().getPath() + "/awse/script.bin");
        if(f.exists())
        {
            Log.i(TAG,"Removed temporatory script on " + Environment.getExternalStorageDirectory().getPath() + "/awse/");
            f.delete();
        }
    }

}
