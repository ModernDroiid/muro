# dnsjava incluye integraciones opcionales (JNDI, JNA, sun.*) que no existen en Android
# y que Muro no usa: solo lee y arma mensajes.
-dontwarn javax.naming.**
-dontwarn com.sun.jna.**
-dontwarn sun.net.spi.nameservice.**
-dontwarn org.xbill.DNS.spi.**
-dontwarn org.slf4j.**
-dontwarn lombok.**

# dnsjava crea cada tipo de registro por reflexión al leer un mensaje.
-keep class org.xbill.DNS.** extends org.xbill.DNS.Record { <init>(...); }
