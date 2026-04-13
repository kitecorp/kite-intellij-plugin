create table if not exists Mimedia.Account
(
    id              bigint unsigned auto_increment
        primary key,
    emailAddress    varchar(100) collate utf8mb4_unicode_ci null,
    password        char(64)                                null,
    firstName       varchar(32) collate utf8mb4_unicode_ci  null,
    lastName        varchar(32) collate utf8mb4_unicode_ci  null,
    createdOn       timestamp                               null,
    modifiedOn      timestamp                               null on update CURRENT_TIMESTAMP,
    statusBits      bigint unsigned                         null,
    telephone       varchar(20) charset utf8mb3             null,
    salt            bigint                                  null,
    twoFactor       tinyint(1) default 0                    not null,
    twoFactorMethod varchar(50) charset utf8mb3             null,
    backupTelephone varchar(20)                             null,
    language        varchar(10)                             null,
    statusUpdatedOn timestamp                               null,
    constraint uc_account_email
        unique (emailAddress)
)
    collate = utf8mb4_general_ci;

create index idx_acct_phone
    on Mimedia.Account (telephone);

