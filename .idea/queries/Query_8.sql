alter user 'wpds'@'%' IDENTIFIED WITH mysql_native_password BY '!@ppl3sAr3Fun';
grant all privileges on wordpresssupport.* to 'wpds'@'localhost' ;
flush privileges;
select Host, User from mysql.user;
