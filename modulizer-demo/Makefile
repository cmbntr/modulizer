.PHONY: genkey export import remove verify p12cert info

genkey:
	./demokeytool -delete -alias thekey 2>/dev/null || true
	./demokeytool -genkeypair -alias thekey -dname "CN=modulizer demo, OU=software demo, O=misc inc" -keyalg RSA -keysize 2048 -validity 3650
	./demokeytool -exportcert -rfc -alias thekey

export:
	mkdir -p target
	./demokeytool -exportcert -rfc -alias thekey -file target/modulizerdemo.cert
	keytool -printcert -v -file target/modulizerdemo.cert

#Note: Webstart might use the browser certificate store (see the control panel)
#      or the cacerts of another JRE (e.g. export JAVA_HOME="/Library/Internet Plug-ins/JavaAppletPlugin.plugin/Contents/Home")
import:
	./cacertskeytool -importcert -noprompt -alias modulizerdemo -file target/modulizerdemo.cert
	./cacertskeytool -list -v -alias modulizerdemo

remove:
	./cacertskeytool -delete -alias modulizerdemo

jar="$(shell find target/webstart/resources -name '*.jar')"
verify:
	keytool -printcert -jarfile $(jar)
	jarsigner -certs -verify $(jar)

p12cert:
	openssl pkcs12 -export -nokeys -in target/modulizerdemo.cert -out target/modulizerdemo_cert.p12 -name modulizerdemo -passout 'pass:'
	openssl pkcs12 -info -in target/modulizerdemo_cert.p12 -passin 'pass:' | openssl x509 -text -noout

info:
	openssl pkcs12 -info -in src/main/demo_keystore.p12 -passin 'pass:s3KreT' -nodes | openssl x509 -text -noout
	openssl x509 -in target/modulizerdemo.cert -text -noout
