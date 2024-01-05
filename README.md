# conn-ewallet-settlement

# Steps generate OneFin public key and private key for settlement with Napas

```shell
Cac buoc tao key 
step1:
openssl genrsa -des3 -out XXX_PGP_Privatekey.key 2048
step2:
openssl req -new -key XXX_PGP_Privatekey.key -out XXX_PGP_Privatekey.csr
step3:
openssl req -new -x509 -days 1001 -key XXX_PGP_Privatekey.key -sha256  -out XXX_PGP_Publickey.cer
step4:
openssl pkcs12 -export -in XXX_PGP_Publickey.cer -inkey XXX_PGP_Privatekey.key -out XXX_PGP.pfx
step5:
openssl pkcs12 -in XXX_PGP.pfx -nocerts -out XXX_PGP_Privatekey.pem
step6:
openssl rsa -in XXX_PGP_Privatekey.pem  -out XXX_PGP_Privatekeys.pem

--> file XXX_PGP_Publickey.cer: dùng để gửi cho Napas
-->XXX_PGP_Privatekeys.pem: dùng để gải mã các file do Napas gửi sang, napas gửi sang sẽ dùng public_key của đối tác để mã hóa, đối tác sẽ dung private key của đối tác đê giải mã và ngược lại

```

# Attachment repository
Date format: ddMMyyyy