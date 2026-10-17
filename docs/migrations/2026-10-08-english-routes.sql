-- Rute halaman memakai bahasa Inggris (nama folder di frontend). Hanya URL menu yang berubah; id dan nama tampilan menu tetap.
BEGIN;
UPDATE public.menu SET url = '/master/branch',          modificated = now() WHERE id = 'BRANCH';
UPDATE public.menu SET url = '/master/product',         modificated = now() WHERE id = 'PRODUCT';
UPDATE public.menu SET url = '/stock/balance',          modificated = now() WHERE id = 'STOCK_BALANCE';
UPDATE public.menu SET url = '/stock/movement',         modificated = now() WHERE id = 'STOCK_MOVEMENT';
UPDATE public.menu SET url = '/stock/transfer',         modificated = now() WHERE id = 'STOCK_TRANSFER';
UPDATE public.menu SET url = '/transaction/cashier',    modificated = now() WHERE id = 'SALE_POS';
UPDATE public.menu SET url = '/transaction/history',    modificated = now() WHERE id = 'SALE_HISTORY';
UPDATE public.menu SET url = '/transaction/return',     modificated = now() WHERE id = 'SALE_RETURN';
UPDATE public.menu SET url = '/transaction/cash',       modificated = now() WHERE id = 'SALE_CASH';
UPDATE public.menu SET url = '/finance/expense',        modificated = now() WHERE id = 'FINANCE_EXPENSE';
UPDATE public.menu SET url = '/finance/petty-cash',     modificated = now() WHERE id = 'FINANCE_PETTY';
UPDATE public.menu SET url = '/finance/report',         modificated = now() WHERE id = 'FINANCE_REPORT';
COMMIT;
