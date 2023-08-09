/*
 * Copyright 2023 NAVER Corp.
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

package com.navercorp.pinpoint.it.plugin.httpclient4;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

public class ClassLoaderTest {

    @Test
    public void test() throws Exception {
        File file = new File("C:/Users/USER/Documents/GitHub/pinpoint/plugins/httpclient4/target/classes");
        URL url = file.toURI().toURL();

        URL[] urls = {url};
        URLClassLoader classLoader = new URLClassLoader(urls);
        Class<?> testClass = classLoader.loadClass("com.navercorp.pinpoint.plugin.httpclient4.HttpClient4TypeProvider");

        System.out.println(testClass);


    }
}
