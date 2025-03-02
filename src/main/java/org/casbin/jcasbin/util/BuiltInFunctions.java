// Copyright 2018 The casbin Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.casbin.jcasbin.util;

import com.googlecode.aviator.AviatorEvaluator;
import com.googlecode.aviator.AviatorEvaluatorInstance;
import com.googlecode.aviator.runtime.function.AbstractVariadicFunction;
import com.googlecode.aviator.runtime.function.FunctionUtils;
import com.googlecode.aviator.runtime.type.*;
import inet.ipaddr.AddressStringException;
import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;
import org.casbin.jcasbin.rbac.RoleManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class BuiltInFunctions {

    private static final Pattern KEY_MATCH2_PATTERN = Pattern.compile(":[^/]+");
    private static final Pattern KEY_MATCH3_PATTERN = Pattern.compile("\\{[^/]+\\}");

    /**
     * keyMatch determines whether key1 matches the pattern of key2 (similar to RESTful path), key2
     * can contain a *.
     *
     * <pre>
     * For example, "/foo/bar" matches "/foo/*"
     * </pre>
     *
     * @param key1 the first argument.
     * @param key2 the second argument.
     * @return whether key1 matches key2.
     */
    public static boolean keyMatch(String key1, String key2) {
        int i = key2.indexOf('*');
        if (i == -1) {
            return key1.equals(key2);
        }

        if (key1.length() > i) {
            return key1.substring(0, i).equals(key2.substring(0, i));
        }
        return key1.equals(key2.substring(0, i));
    }

    /**
     * keyMatch2 determines whether key1 matches the pattern of key2 (similar to RESTful path), key2
     * can contain a *.
     *
     * <pre>
     * For example, "/foo/bar" matches "/foo/*", "/resource1" matches "/:resource"
     * </pre>
     *
     * @param key1 the first argument.
     * @param key2 the second argument.
     * @return whether key1 matches key2.
     */
    public static boolean keyMatch2(String key1, String key2) {
        key2 = key2.replace("/*", "/.*");
        key2 = KEY_MATCH2_PATTERN.matcher(key2).replaceAll("[^/]+");
        if(Objects.equals(key2, "*")) {
            key2 = "(.*)";
        }
        return regexMatch(key1, "^" + key2 + "$");
    }

    /**
     * keyMatch3 determines whether key1 matches the pattern of key2 (similar to RESTful path), key2
     * can contain a *.
     *
     * <pre>
     * For example, "/foo/bar" matches "/foo/*", "/resource1" matches "/{resource}"
     * </pre>
     *
     * @param key1 the first argument.
     * @param key2 the second argument.
     * @return whether key1 matches key2.
     */
    public static boolean keyMatch3(String key1, String key2) {
        key2 = key2.replace("/*", "/.*");
        key2 = "^" + KEY_MATCH3_PATTERN.matcher(key2).replaceAll("[^/]+") + "$";
        try {
            return regexMatch(key1, key2);
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    /**
     * KeyMatch4 determines whether key1 matches the pattern of key2 (similar to RESTful path), key2
     * can contain a *. Besides what KeyMatch3 does, KeyMatch4 can also match repeated patterns:
     *
     * <pre>
     * "/parent/123/child/123" matches "/parent/{id}/child/{id}"
     * "/parent/123/child/456" does not match "/parent/{id}/child/{id}"
     * But KeyMatch3 will match both.
     * </pre>
     *
     * Attention: key1 cannot contain English commas.
     *
     * @param key1 the first argument.
     * @param key2 the second argument.
     * @return whether key1 matches key2.
     */
    public static boolean keyMatch4(String key1, String key2) {
        String regEx = "\\{[^/]+\\}";
        Pattern p = Pattern.compile(regEx);
        Matcher m = p.matcher(key2);

        String[] tmp = p.split(key2);
        List<String> tokens = new ArrayList<>();
        if (tmp.length > 0) {
            int count = 0;
            while (count < tmp.length) {
                tokens.add(tmp[count]);
                if (m.find()) {
                    tokens.add(m.group());
                }
                count++;
            }
        }
        int off = 0;
        for (String token : tokens) {
            if (!p.matcher(token).matches()) {
                while (off < key1.length() && key1.charAt(off) != token.charAt(0)) {
                    off++;
                }
                if (key1.length() - (off + 1) < token.length()) {
                    return false;
                }
                if (!key1.startsWith(token, off)) {
                    return false;
                }
                key1 = key1.replaceFirst(token, ",");
            }
        }
        String[] values = key1.split(",");
        int i = 0;
        Map<String, String> params = new HashMap<>();
        for (String token : tokens) {
            if (p.matcher(token).matches()) {
                while (i < values.length && "".equals(values[i])) {
                    i++;
                }
                if (i == values.length) {
                    return false;
                }
                if (params.containsKey(token)) {
                    if (!values[i].equals(params.get(token))) {
                        return false;
                    }
                } else {
                    params.put(token, values[i]);
                }
                i++;
            }
        }
        return true;
    }

    /**
     * KeyMatch5 determines whether key1 matches the pattern of key2 and ignores the parameters in key2.
     *
     * <pre>
     * For example, "/foo/bar?status=1&type=2" matches "/foo/bar"
     * </pre>
     *
     * @param key1 the first argument.
     * @param key2 the second argument.
     * @return whether key1 matches key2.
     */
    public static boolean keyMatch5(String key1, String key2) {
        int i = key1.indexOf('?');
        if (i == -1) {
            return key1.equals(key2);
        }
        return key1.substring(0, i).equals(key2);
    }

    /**
     * KeyGet returns the matched part. For example, "/foo/bar/foo" matches "/foo/*", "bar/foo" will been returned
     *
     * @param key1 the first argument.
     * @param key2 the second argument.
     * @return the matched part.
     */
    public static String keyGetFunc(String key1, String key2) {
        int index = key2.indexOf('*');
        if (index == -1) {
            return "";
        }
        if (key1.length() > index) {
            if (key1.substring(0, index).equals(key2.substring(0, index))) {
                return key1.substring(index);
            }
        }
        return "";
    }

    /**
     * KeyGet2 returns value matched pattern.For example, "/resource1" matches "/:resource", if the pathVar == "resource", then "resource1" will be returned.
     *
     * @param key1 the first argument.
     * @param key2 the second argument.
     * @return the matched part.
     */
    public static String keyGet2Func(String key1, String key2, String pathVar) {
        key2 = key2.replace("/*", "/.*");
        String regexp = ":[^/]+";
        Pattern re = Pattern.compile(regexp);
        Matcher keys = re.matcher(key2);
        List<String> keysList = new ArrayList<>();
        while (keys.find()) {
            keysList.add(keys.group());
        }
        key2 = keys.replaceAll("([^/]+)");
        key2 = "^" + key2 + "$";
        Pattern re2 = Pattern.compile(key2);
        Matcher values = re2.matcher(key1);
        List<String> valuesList = new ArrayList<>();
        while (values.find()) {
            for (int i = 0; i <= values.groupCount(); i++) {
                valuesList.add(values.group(i));
            }
        }
        if (valuesList.isEmpty()) {
            return "";
        }
        for (int i = 0; i < keysList.size(); i++) {
            if (pathVar.equals(keysList.get(i).substring(1))) {
                return valuesList.get(i + 1);
            }
        }
        return "";
    }

    /**
     * regexMatch determines whether key1 matches the pattern of key2 in regular expression.
     *
     * @param key1 the first argument.
     * @param key2 the second argument.
     * @return whether key1 matches key2.
     */
    public static boolean regexMatch(String key1, String key2) {
        return Pattern.compile(key2).matcher(key1).lookingAt();
    }

    /**
     * ipMatch determines whether IP address ip1 matches the pattern of IP address ip2, ip2 can be
     * an IP address or a CIDR pattern. For example, "192.168.2.123" matches "192.168.2.0/24"
     *
     * @param ip1 the first argument.
     * @param ip2 the second argument.
     * @return whether ip1 matches ip2.
     */
    public static boolean ipMatch(String ip1, String ip2) {
        IPAddressString ipas1 = new IPAddressString(ip1);
        try {
            ipas1.validateIPv4();
        } catch (AddressStringException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("invalid argument: ip1 in IPMatch() function is not an IP address.");
        }

        IPAddressString ipas2 = new IPAddressString(ip2);
        try {
            ipas2.validate();
        } catch (AddressStringException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("invalid argument: ip2 in IPMatch() function is neither an IP address nor a CIDR.");
        }

        if (ipas1.equals(ipas2)) {
            return true;
        }

        IPAddress ipa1;
        IPAddress ipa2;
        try {
            ipa1 = ipas1.toAddress();
            ipa2 = ipas2.toAddress();
        } catch (AddressStringException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("invalid argument: ip1 or ip2 in IPMatch() function is not an IP address.");
        }

        Integer prefix = ipa2.getNetworkPrefixLength();
        IPAddress mask = ipa2.getNetwork().getNetworkMask(prefix, false);
        return ipa1.mask(mask).equals(ipa2.mask(mask));
    }

    /**
     * globMatch determines whether key1 matches the pattern of key2 in glob expression.
     *
     * @param key1 the first argument.
     * @param key2 the second argument.
     * @return whether key1 matches key2.
     */
    public static boolean globMatch(String key1, String key2) {
        return Pattern.matches(Glob.toRegexPattern(key2), key1);
    }

    /**
     * allMatch determines whether key1 matches the pattern of key2 , key2 can contain a *.
     *
     * <pre>
     * For example, "*" matches everything
     * </pre>
     *
     * @param key1 the first argument.
     * @param key2 the second argument.
     * @return whether key1 matches key2.
     */
    public static boolean allMatch(String key1, String key2) {
        if ("*".equals(key2)) {
            return true;
        }

        return key1.equals(key2);
    }


    public static class GenerateGFunctionClass {
        // key:name such as g,g2  value:user-role mapping
        private static Map<String, Map<String, AviatorBoolean>> memorizedMap = new ConcurrentHashMap<>();

        /**
         * generateGFunction is the factory method of the g(_, _) function.
         *
         * @param name the name of the g(_, _) function, can be "g", "g2", ..
         * @param rm   the role manager used by the function.
         * @return the function.
         */
        public static AviatorFunction generateGFunction(String name, RoleManager rm) {
            memorizedMap.put(name, new ConcurrentHashMap<>());

            return new AbstractVariadicFunction() {
                @Override
                public AviatorObject variadicCall(Map<String, Object> env, AviatorObject... args) {
                    Map<String, AviatorBoolean> memorized = memorizedMap.get(name);
                    int len = args.length;
                    if (len < 2) {
                        return AviatorBoolean.valueOf(false);
                    }
                    String name1 = FunctionUtils.getStringValue(args[0], env);
                    String name2 = FunctionUtils.getStringValue(args[1], env);

                    String key = "";
                    for (AviatorObject arg : args) {
                        String name = FunctionUtils.getStringValue(arg, env);
                        key += ";" + name;
                    }

                    AviatorBoolean value = memorized.get(key);
                    if (value != null) {
                        return value;
                    }

                    if (rm == null) {
                        value = AviatorBoolean.valueOf(name1.equals(name2));
                    } else if (len == 2) {
                        value = AviatorBoolean.valueOf(rm.hasLink(name1, name2));
                    } else if (len == 3) {
                        String domain = FunctionUtils.getStringValue(args[2], env);
                        value = AviatorBoolean.valueOf(rm.hasLink(name1, name2, domain));
                    } else {
                        value = AviatorBoolean.valueOf(false);
                    }
                    memorized.put(key, value);
                    return value;
                }

                @Override
                public String getName() {
                    return name;
                }
            };
        }
    }

    /**
     * eval calculates the stringified boolean expression and return its result.
     *
     * @param eval        the stringified boolean expression.
     * @param env         the key-value pair of the parameters in the expression.
     * @param aviatorEval the AviatorEvaluatorInstance object which contains built-in functions and custom functions.
     * @return the result of the eval.
     */
    public static boolean eval(String eval, Map<String, Object> env, AviatorEvaluatorInstance aviatorEval) {
        boolean res;
        if (aviatorEval != null) {
            try {
                res = (boolean) aviatorEval.execute(eval, env);
            } catch (Exception e) {
                res = false;
            }
        } else {
            res = (boolean) AviatorEvaluator.execute(eval, env);
        }
        return res;
    }
}
