package org.greenrobot.eventbus.annotationprocessor;

/**
 * @author jaydroid
 * @version 1.0
 * @date 6/11/21
 */
class StringUtils {

    public static boolean isNotEmpty(CharSequence warning) {
        return warning != null && !warning.toString().isEmpty();
    }

}
