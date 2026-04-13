SELECT
    'Total (original) storage' AS 'Telcel users',
    ROUND(COALESCE(SUM(byteCount) / 1000000000, 0), 3) AS 'Gb'
FROM
    Content.File
WHERE
    accountId IN (SELECT
                      acc.id
                  FROM
                      Mimedia.Account acc,
                      Mimedia.AccountProduct ap,
                      Product.Product pr
                  WHERE
                      acc.id = ap.accountId
                    AND ap.productId = pr.id
                    AND pr.name IN ('Free Plan Telcel' , 'Cloud MiMedia 25GB',
                                    'Cloud MiMedia 100GB',
                                    'Cloud MiMedia 250GB',
                                    'Cloud MiMedia 1TB'))
  AND state NOT IN ('forDeletion' , 'deleted')
UNION SELECT
          'Total (original + transformed) storage',
          ROUND((COALESCE((SELECT
                               SUM(byteCount)
                           FROM
                               Content.File
                           WHERE
                               accountId IN (SELECT
                                                 acc.id
                                             FROM
                                                 Mimedia.Account acc,
                                                 Mimedia.AccountProduct ap,
                                                 Product.Product pr
                                             WHERE
                                                 acc.id = ap.accountId
                                               AND ap.productId = pr.id
                                               AND pr.name IN ('Free Plan Telcel' , 'Cloud MiMedia 25GB',
                                                               'Cloud MiMedia 100GB',
                                                               'Cloud MiMedia 250GB',
                                                               'Cloud MiMedia 1TB'))
                             AND state NOT IN ('forDeletion' , 'deleted')),
                          0) + COALESCE((SELECT
                                             SUM(tf.byteCount)
                                         FROM
                                             Content.TransformedFile tf
                                                 JOIN
                                             Content.File f ON tf.fileId = f.id
                                         WHERE
                                             f.accountId IN (SELECT
                                                                 acc.id
                                                             FROM
                                                                 Mimedia.Account acc,
                                                                 Mimedia.AccountProduct ap,
                                                                 Product.Product pr
                                                             WHERE
                                                                 acc.id = ap.accountId
                                                               AND ap.productId = pr.id
                                                               AND pr.name IN ('Free Plan Telcel' , 'Cloud MiMedia 25GB',
                                                                               'Cloud MiMedia 100GB',
                                                                               'Cloud MiMedia 250GB',
                                                                               'Cloud MiMedia 1TB'))),
                                        0) + COALESCE((SELECT
                                                           SUM(fvi.byteCount)
                                                       FROM
                                                           Content.FileVersionImageUrl fvi
                                                               JOIN
                                                           Content.File f ON fvi.fileId = f.id
                                                       WHERE
                                                           f.accountId IN (SELECT
                                                                               acc.id
                                                                           FROM
                                                                               Mimedia.Account acc,
                                                                               Mimedia.AccountProduct ap,
                                                                               Product.Product pr
                                                                           WHERE
                                                                               acc.id = ap.accountId
                                                                             AND ap.productId = pr.id
                                                                             AND pr.name IN ('Free Plan Telcel' , 'Cloud MiMedia 25GB',
                                                                                             'Cloud MiMedia 100GB',
                                                                                             'Cloud MiMedia 250GB',
                                                                                             'Cloud MiMedia 1TB'))),
                                                      0)) / 1000000000, 3)