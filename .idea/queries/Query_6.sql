create table if not exists Product.Product
(
    id         bigint unsigned auto_increment
        primary key,
    active     tinyint(1)                              not null,
    name       varchar(50)                             not null,
    capacity   int       default 1                     not null,
    plan_level int       default 1                     not null,
    createdOn  timestamp default CURRENT_TIMESTAMP     not null on update CURRENT_TIMESTAMP,
    modifiedOn timestamp default '0000-00-00 00:00:00' not null
);

create index Product__index_name
    on Product.Product (name);

