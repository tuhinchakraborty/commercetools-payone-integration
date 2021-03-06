# Migration Guide

<!-- START doctoc generated TOC please keep comment here to allow auto update -->
<!-- DON'T EDIT THIS SECTION, INSTEAD RE-RUN doctoc TO UPDATE -->


- [To v2+ (Multitenancy)](#to-v2-multitenancy)
  - [1. Integration Service changes](#1-integration-service-changes)
  - [2. Payone portal changes](#2-payone-portal-changes)
  - [3. Changes in the shops which uses the service](#3-changes-in-the-shops-which-uses-the-service)
  - [4. Setup a new tenant (branch,shop,merchant)](#4-setup-a-new-tenant-branchshopmerchant)
  - [5. Important notes](#5-important-notes)
- [To v2.2+ (`Initial` transaction state)](#to-v22-initial-transaction-state)
- [To v2.3+ (`Paydirekt` payment method)](#to-v23-paydirekt-payment-method)
- [To v2.12+ (support for `bancontact` and `iDeal` payment methods)](#to-v212-support-for-bancontact-and-ideal-payment-methods)

<!-- END doctoc generated TOC please keep comment here to allow auto update -->

## To v2+ (Multitenancy)

Version 2 introduced multi-tenancy feature. This is a ***breaking-change*** thus requires additional steps in the 
service and Payone provider setup.

**Note**: after version 2 release the first version likely will be abandoned hence we recommend to do the migration as 
soon as it is possible for your project.

If you are setting up the project from scratch - likely you should just follow [Multitenancy](/README.md#multitenancy) 
section in the main documentation page.

### 1. Integration Service changes

1. One mandatory property added: `TENANTS`, a comma (or semicolon) separate list of your tenants (branches, franchising etc).
This value will be exposed as a part of handle and notification URLs. 

    If you have exactly one tenant - put a single name. For example:
    ```
    TENANTS=MAIN_SHOP
    ```
    
    Read [Mandatory common properties](/README.md#mandatory-common-properties) section for more details 
    and the names restrictions.

1. Add tenant name as a prefix to tenant-specific environment variables/properties 
(note, underscore **_** is required between the prefix and variable name):

Old variable name               | New variable name (for single tenant with name `MAIN_SHOP` | Mandatory
--------------------------------|------------------------------------------------------------| ---------
`CT_PROJECT_KEY`                | `MAIN_SHOP_CT_PROJECT_KEY`                                 | **Yes**
`CT_CLIENT_ID`                  | `MAIN_SHOP_CT_CLIENT_ID`                                   | **Yes**
`CT_CLIENT_SECRET`              | `MAIN_SHOP_CT_CLIENT_SECRET`                               | **Yes**
`CT_START_FROM_SCRATCH`         | `MAIN_SHOP_CT_START_FROM_SCRATCH`                          | No
`PAYONE_KEY`                    | `MAIN_SHOP_PAYONE_KEY`                                     | **Yes**
`PAYONE_MERCHANT_ID`            | `MAIN_SHOP_PAYONE_MERCHANT_ID`                             | **Yes**
`PAYONE_MODE`                   | `MAIN_SHOP_PAYONE_MODE`                                    | **Yes**
`PAYONE_PORTAL_ID`              | `MAIN_SHOP_PAYONE_PORTAL_ID`                               | **Yes**
`PAYONE_SUBACC_ID`              | `MAIN_SHOP_PAYONE_SUBACC_ID`                               | **Yes**
`PAYONE_MODE`                   | `MAIN_SHOP_PAYONE_MODE`                                    | No
`UPDATE_ORDER_PAYMENT_STATE`    | `MAIN_SHOP_UPDATE_ORDER_PAYMENT_STATE`                     | No
`SECURE_KEY`                    | `MAIN_SHOP_SECURE_KEY`                                     | No



### 2. Payone portal changes

In Payone go to [Payment portals](https://pmi.pay1.de/merchants/?navi=portal&list=1) page, edit portal with id 
`PAYONE_PORTAL_ID` and:

Tab        | Field name              | Old value                                                   | New value | Comment
-----------|-------------------------|-------------------------------------------------------------|-----------------------------------------------------------------------------------------|---------
_Extended_ | `TransactionStatus URL` | `https://payone-integration.myshop.com/payone/notification` | <code>https://payone-integration.myshop.com/<b>MAIN_SHOP</b>/payone/notification</code> | **Mandatory**
_General_  | `URL`                   | `https://payone-integration.myshop.com`                     | <code>https://payone-integration.myshop.com/<b>MAIN_SHOP</b>                            | Optional

### 3. Changes in the shops which uses the service

After you setup the service and respective Payone portal - update a shop settings. 
Find a place where URL request the integration service is configured. 
You have to change the payment handling URL:
  1. The old URL expected to be a string containing `/commercetools/handle/payments/`
  1. Insert your default tenant name (like `MAIN_SHOP`) into this path: <code>**MAIN_SHOP**/commercetools/handle/payments/</code>

### 4. Setup a new tenant (branch,shop,merchant)

  1. Add a new tenant name to `TENANTS`, like `TENANTS=MAIN_SHOP,MERCHANT1`
  1. [Add a new portal in Payone](https://pmi.pay1.de/merchants/?navi=portal) with `MERCHANT1` path part in the notification URL
  1. [Add a new commercetools platform account](https://admin.commercetools.com/) 
  1. In the service settings (environment variables) add the same properties, but with `MERCHANT1_` prefix instead of `MAIN_SHOP_`
  1. In the new shop configure payment checkout service to connect to the new merchant URL 
    (like <code>**MERCHANT1**/commercetools/handle/payments/</code>)
    
### 5. Important notes
  - For every tenant you should use separate Payone Portal ID,
  otherwise you won't be able to receive property payment update nofications,
  because notification URL is configured on portal level
  (see [2. Payone portal changes](#2-payone-portal-changes) and 
  [4. Setup a new tenant (branch,shop,merchant)](#4-setup-a-new-tenant-branchshopmerchant) above)


## To v2.2+ (`Initial` transaction state)

  1. It is strongly recommended by CTP platform developers that new transactions are added to a payment with explicitly
  set `Initial` transaction state. In version v2.2.+ the service still supports handling both `Initial` and `Pending` 
  transaction states for payments handling, but this will be changed in the next releases, 
  so only transactions with `Initial` will be handled and sent to Payone service, when `Pending` transaction will be 
  expected as processed by Payone already. 
  
      For more info see:
      
      - [Transaction States](https://dev.commercetools.com/http-api-projects-payments.html#transactionstate) documentation
    
      - [Release Notes](http://dev.commercetools.com/release-notes.html#release-notes---commercetools-platform---version-release-29-september-2017)

      - [issue #175](https://github.com/commercetools/commercetools-payone-integration/pull/175)
  
  1. All references and update actions for `amountAuthorized`, `amountPaid` and `amountRefunded` are removed 
  since they are deprecated. The service consumers should not rely on them any more.  
  
      See [Release Notes](http://dev.commercetools.com/release-notes.html#release-notes---commercetools-platform---version-release-29-september-2017)

  1. Note, that property _Method hash calculation_ in 
  `https://pmi.pay1.de -> Configuration -> Payment Portals -> Extended -> Method hash calculation`) 
  is responsible only for _client API_. As talked to Payone support, server API remains on MD5 and 
  there is no any plans to update it soon.
  
## To v2.3+ (`Paydirekt` payment method)
  
  _This section could be skipped if this payment method is not used_
  
  1. Create Paydirekt Merchant account.
  1. Contact Payone Merchant support and activate Paydirekt payment method in your account, 
  supply them required values from Paydirekt Merchant account.
  1. Ensure the following values are always specified in `Cart#shippingAddress` before payment handling:
     * **`firstName`**
     * **`lastName`**
     * **`postalCode`**
     * **`city`**
     * **`country`** (**note**: this is independent from `Cart#country` and may be different)
     
     These fields are mandatory for Paydirekt payments according to  _TECHNICAL REFERENCE Add-On for Paydirekt v 1.5_.
     This is different from other Wallet payments, like PayPal.
     
     The rest of the payment integration is similar to other redirect based payments, like PayPal or Sofortüberweisung.
     
     See more in [Field Mapping Guide](/docs/Field-Mapping.md)

## To v2.12+ (support for `bancontact` and `iDeal` payment methods)
  
  _This section could be skipped if this payment method is not used_
  
  1. Contact Payone Merchant support and activate `bancontact` and `iDeal` payment method in your account.
  1. Ensure the following attributes are added to payment type `payment-BANK_TRANSFER`:
     * **`bankCountry`**
     * **`bankGroupType`**
     If attributes are missing but the payment type already exists, add them by using the following update actions:
        ```json
        {
          "version": xxx,
          "actions": [
            {
              "action": "addFieldDefinition",
              "fieldDefinition": {
                "name": "bankCountry",
                "label": {
                  "en": "bankCountry",
                  "de": "bankCountry"
                },
                "required": false,
                "type": {
                  "name": "String"
                },
                "inputHint": "SingleLine"
              }
            },
            {
              "action": "addFieldDefinition",
              "fieldDefinition": {
                "name": "bankCountry",
                "label": {
                  "en": "bankCountry",
                  "de": "bankCountry"
                },
                "required": false,
                "type": {
                  "name": "String"
                },
                "inputHint": "SingleLine"
              }
            }
          ]
        }
        ```   
  1. Ensure the following values are always specified in `Payment#custom#fields` before payment handling:
     * **`bankCountry`** - this field is mandatory for bancontact and iDeal payments
     * **`bankGroupType`** - this field is mandatory for iDeal payment
     
     The rest of the payment integration is similar to other redirect based payments, like PayPal or Sofortüberweisung.
     
     See more in [Field Mapping Guide](/docs/Field-Mapping.md)
