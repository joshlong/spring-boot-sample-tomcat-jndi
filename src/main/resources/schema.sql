drop table if  exists  customers ;
create table customers
(
    id   serial primary key,
    name varchar(255) not null
);