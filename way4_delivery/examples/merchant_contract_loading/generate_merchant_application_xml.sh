#!/usr/bin/env bash
set -euo pipefail

required=(
  WAY4_SENDER WAY4_INSTITUTION WAY4_ORDER_DPRT WAY4_FILE_NUMBER
  WAY4_CLIENT_TYPE WAY4_CLIENT_REG_NUMBER WAY4_CLIENT_SHORT_NAME
  WAY4_COMPANY_NAME WAY4_COUNTRY WAY4_CITY WAY4_ADDRESS_LINE1
  WAY4_ACCOUNT_CONTRACT_NUMBER WAY4_ACCOUNT_CONTRACT_NAME
  WAY4_ACCOUNT_PRODUCT_CODE WAY4_PAYMENT_ADDRESS_TYPE
  WAY4_DEVICE_CONTRACT_NUMBER WAY4_DEVICE_CONTRACT_NAME
  WAY4_DEVICE_PRODUCT_CODE WAY4_DEVICE_TYPE WAY4_MID WAY4_MCC
  WAY4_CURRENCY WAY4_DEVICE_LOCATION
)

for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    printf 'Variable obligatoire absente: %s\n' "$name" >&2
    exit 2
  fi
done

if [[ ! "$WAY4_MID" =~ ^[[:print:]]{1,15}$ ]]; then
  printf 'WAY4_MID doit contenir entre 1 et 15 caractères ASCII imprimables.\n' >&2
  exit 2
fi

if [[ ! "$WAY4_DEVICE_CONTRACT_NUMBER" =~ ^[[:alnum:]]{8}$ ]]; then
  printf 'WAY4_DEVICE_CONTRACT_NUMBER doit contenir 8 caractères alphanumériques pour ce gabarit POS.\n' >&2
  exit 2
fi

if [[ ! "$WAY4_MCC" =~ ^[[:digit:]]{4}$ ]]; then
  printf 'WAY4_MCC doit contenir 4 chiffres.\n' >&2
  exit 2
fi

xml_escape() {
  local value=${1-}
  value=${value//&/\&amp;}
  value=${value//</\&lt;}
  value=${value//>/\&gt;}
  value=${value//\"/\&quot;}
  value=${value//\'/\&apos;}
  printf '%s' "$value"
}

today=$(date +%Y-%m-%d)
now=$(date +%H:%M:%S)
julian=$(date +%j)
timestamp=$(date +%Y%m%d%H%M%S)
sender6=$(printf '%-6s' "$WAY4_SENDER" | tr ' ' '0')
file_number5=$(printf '%05d' "$((10#$WAY4_FILE_NUMBER))")
output_dir=${WAY4_XML_OUTPUT_DIR:-./out}
mkdir -p "$output_dir"
output_file="$output_dir/XADVAPL${sender6}_${file_number5}.${julian}"

client_app="ACQ-CLIENT-${timestamp}"
account_app="ACQ-ACCOUNT-${timestamp}"
address_app="ACQ-ADDR-${timestamp}"
device_app="ACQ-DEVICE-${timestamp}"

cat >"$output_file" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<ApplicationFile>
  <FileHeader>
    <FormatVersion>2.0</FormatVersion>
    <Sender>$(xml_escape "$WAY4_SENDER")</Sender>
    <CreationDate>$today</CreationDate>
    <CreationTime>$now</CreationTime>
    <Number>$((10#$WAY4_FILE_NUMBER))</Number>
    <Institution>$(xml_escape "$WAY4_INSTITUTION")</Institution>
  </FileHeader>
  <ApplicationsList>
    <Application>
      <RegNumber>$client_app</RegNumber>
      <Institution>$(xml_escape "$WAY4_INSTITUTION")</Institution>
      <OrderDprt>$(xml_escape "$WAY4_ORDER_DPRT")</OrderDprt>
      <ObjectType>Client</ObjectType>
      <ActionType>Add</ActionType>
      <ProductCategory>Acquiring</ProductCategory>
      <Data>
        <Client>
          <ClientType>$(xml_escape "$WAY4_CLIENT_TYPE")</ClientType>
          <ClientCategory>Commercial</ClientCategory>
          <ClientInfo>
            <RegNumber>$(xml_escape "$WAY4_CLIENT_REG_NUMBER")</RegNumber>
            <ShortName>$(xml_escape "$WAY4_CLIENT_SHORT_NAME")</ShortName>
            <TaxpayerIdentifier>$(xml_escape "${WAY4_TAX_ID:-}")</TaxpayerIdentifier>
            <CompanyName>$(xml_escape "$WAY4_COMPANY_NAME")</CompanyName>
            <CompanyTradeName>$(xml_escape "${WAY4_TRADE_NAME:-$WAY4_COMPANY_NAME}")</CompanyTradeName>
          </ClientInfo>
          <BaseAddress>
            <Country>$(xml_escape "$WAY4_COUNTRY")</Country>
            <City>$(xml_escape "$WAY4_CITY")</City>
            <PostalCode>$(xml_escape "${WAY4_POSTAL_CODE:-}")</PostalCode>
            <AddressLine1>$(xml_escape "$WAY4_ADDRESS_LINE1")</AddressLine1>
          </BaseAddress>
        </Client>
      </Data>
      <SubApplList>
        <Application>
          <RegNumber>$account_app</RegNumber>
          <Institution>$(xml_escape "$WAY4_INSTITUTION")</Institution>
          <OrderDprt>$(xml_escape "$WAY4_ORDER_DPRT")</OrderDprt>
          <ObjectType>Contract</ObjectType>
          <ActionType>Add</ActionType>
          <ProductCategory>Acquiring</ProductCategory>
          <Data>
            <Contract>
              <ClientType>$(xml_escape "$WAY4_CLIENT_TYPE")</ClientType>
              <ClientCategory>Commercial</ClientCategory>
              <ContractIDT>
                <ContractNumber>$(xml_escape "$WAY4_ACCOUNT_CONTRACT_NUMBER")</ContractNumber>
              </ContractIDT>
              <ContractName>$(xml_escape "$WAY4_ACCOUNT_CONTRACT_NAME")</ContractName>
              <Currency>$(xml_escape "$WAY4_CURRENCY")</Currency>
              <Product>
                <ProductCode1>$(xml_escape "$WAY4_ACCOUNT_PRODUCT_CODE")</ProductCode1>
                <AccountScheme>$(xml_escape "${WAY4_ACCOUNT_SCHEME:-}")</AccountScheme>
                <ServicePack>$(xml_escape "${WAY4_SERVICE_PACK:-}")</ServicePack>
              </Product>
            </Contract>
          </Data>
          <SubApplList>
            <Application>
              <RegNumber>$address_app</RegNumber>
              <Institution>$(xml_escape "$WAY4_INSTITUTION")</Institution>
              <OrderDprt>$(xml_escape "$WAY4_ORDER_DPRT")</OrderDprt>
              <ObjectType>ContractAddress</ObjectType>
              <ActionType>Add</ActionType>
              <ProductCategory>Acquiring</ProductCategory>
              <Data>
                <Address>
                  <AddressType>$(xml_escape "$WAY4_PAYMENT_ADDRESS_TYPE")</AddressType>
                  <Country>$(xml_escape "$WAY4_COUNTRY")</Country>
                  <City>$(xml_escape "$WAY4_CITY")</City>
                  <PostalCode>$(xml_escape "${WAY4_POSTAL_CODE:-}")</PostalCode>
                  <AddressLine1>$(xml_escape "$WAY4_COMPANY_NAME")</AddressLine1>
                  <AddressLocation>$(xml_escape "$WAY4_CITY")</AddressLocation>
                </Address>
              </Data>
            </Application>
            <Application>
              <RegNumber>$device_app</RegNumber>
              <Institution>$(xml_escape "$WAY4_INSTITUTION")</Institution>
              <OrderDprt>$(xml_escape "$WAY4_ORDER_DPRT")</OrderDprt>
              <ObjectType>Contract</ObjectType>
              <ActionType>Add</ActionType>
              <ProductCategory>Acquiring</ProductCategory>
              <Data>
                <Contract>
                  <ClientType>$(xml_escape "$WAY4_CLIENT_TYPE")</ClientType>
                  <ClientCategory>Commercial</ClientCategory>
                  <ContractIDT>
                    <ContractNumber>$(xml_escape "$WAY4_DEVICE_CONTRACT_NUMBER")</ContractNumber>
                  </ContractIDT>
                  <ContractName>$(xml_escape "$WAY4_DEVICE_CONTRACT_NAME")</ContractName>
                  <Currency>$(xml_escape "$WAY4_CURRENCY")</Currency>
                  <Product>
                    <ProductCode1>$(xml_escape "$WAY4_DEVICE_PRODUCT_CODE")</ProductCode1>
                  </Product>
                  <DeviceInfo>
                    <SIC>$(xml_escape "$WAY4_MCC")</SIC>
                    <MerchantID>$(xml_escape "$WAY4_MID")</MerchantID>
                    <DeviceRecord>
                      <DeviceType>$(xml_escape "$WAY4_DEVICE_TYPE")</DeviceType>
                      <Location>$(xml_escape "$WAY4_DEVICE_LOCATION")</Location>
                      <DefaultCurr>$(xml_escape "$WAY4_CURRENCY")</DefaultCurr>
                      <DeviceConfig>
                        <Status>NotConfigured</Status>
                      </DeviceConfig>
                    </DeviceRecord>
                  </DeviceInfo>
                </Contract>
              </Data>
            </Application>
          </SubApplList>
        </Application>
      </SubApplList>
    </Application>
  </ApplicationsList>
</ApplicationFile>
XML

if command -v xmllint >/dev/null 2>&1; then
  xmllint --noout "$output_file"
  if [[ -n "${WAY4_XSD:-}" ]]; then
    xmllint --noout --schema "$WAY4_XSD" "$output_file"
  else
    printf 'XML bien formé. Validation XSD ignorée: WAY4_XSD non défini.\n'
  fi
else
  printf 'Avertissement: xmllint absent; bien-formation et XSD non contrôlés.\n' >&2
fi

printf 'Fichier généré: %s\n' "$output_file"
