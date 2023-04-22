package cn.nova;

import java.io.*;
import java.util.HashMap;

/**
 * {@link Configure}是一个配置文件的读取和具体配置提供方
 *
 * @author RealDragonking
 */
public final class Configure {

    public Configure(SourceConfigure sourceConfig) {

    }

    /**
     * 读取文件并创建一个{@link Configure}。如果配置文件不存在，则尝试从jar包中复制一份
     *
     * @return {@link Configure}
     */
    public static Configure init() {
        SourceConfigure sourceConfig = new SourceConfigure();
        File configFile = new File("./config");

        byte[] bytes = new byte[2048];
        int len;

        try {
            if (configFile.createNewFile()) {
                try (InputStream source = Configure.class.getResourceAsStream("/config");
                     OutputStream target = new FileOutputStream(configFile)) {
                    if (source == null) {
                        return null;
                    }
                    while ((len = source.read(bytes)) > -1) {
                        target.write(bytes, 0, len);
                    }
                }
            }

            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                reader.lines().forEach(line -> {
                    if (! line.startsWith("#")) {
                        String[] pair = line.split("=");
                        if (pair.length == 2) {
                            sourceConfig.put(pair[0].trim(), pair[1].trim());
                        }
                    }
                });
            }
        } catch (IOException e) {
            return null;
        }

        return new Configure(sourceConfig);
    }

    /**
     * {@link SourceConfigure}具备将{@link String}转换为具体类型的配置项的能力
     *
     * @author RealDragonking
     */
    private static class SourceConfigure extends HashMap<String, String> {

        /**
         * 使用给定的key，获取到int类型的value。如果对应value不存在，则返回默认值defVal
         *
         * @param key 键
         * @param defVal 默认值
         * @return 值
         */
        private int getIntValue(String key, int defVal) {
            try {
                return Integer.parseInt(get(key));
            } catch (Exception e) {
                return defVal;
            }
        }

        /**
         * 使用给定的key，获取到long类型的value。如果对应value不存在，则返回默认值defVal
         *
         * @param key 键
         * @param defVal 默认值
         * @return 值
         */
        private long getLongValue(String key, long defVal) {
            try {
                return Long.parseLong(get(key));
            } catch (Exception e) {
                return defVal;
            }
        }

    }

}
