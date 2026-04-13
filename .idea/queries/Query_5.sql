create table if not exists Billing.Partner
(
    id        bigint auto_increment
        primary key,
    createdOn datetime(6) null,
    settings  json        null,
    updatedOn datetime(6) null
);

