package com.saasno.utility.model.data;

import java.math.BigInteger;
import java.util.*;
import jakarta.persistence.*;
import jakarta.persistence.metamodel.EntityType;

import com.saasno.filter.ColumnFilter;
import com.saasno.utility.verification.StringVerification;

public class DataTable<T> {

    private static final java.util.regex.Pattern IDENTIFIER = java.util.regex.Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final EntityManager entityManager;

    public DataTable(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public DataResult<T> getData(Class<T> entityClass, DataFilter dataFilters) {
        return getData(entityClass, dataFilters, false);
    }

    public DataResult<T> getData(Class<T> entityClass, DataFilter dataFilters, boolean lock) {

        String root = "e";
        StringBuilder baseQuery = new StringBuilder("FROM " + entityClass.getSimpleName() + " " + root + " ");
        StringBuilder joins = new StringBuilder();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");

        Map<String, Object> paramValues = new HashMap<>();
        Set<String> createdJoins = new HashSet<>();

        // ==========================
        // 1. HANDLE DATA FILTER
        // ==========================
        if (dataFilters.getColumn() != null) {
            int index = 0;
            for (ColumnFilter col : dataFilters.getColumn()) {
                String jpqlField = resolveField(entityClass, col.getColumn(), root, joins, createdJoins);
                if (jpqlField == null)
                    continue;

                String operator = col.getOperator().toUpperCase();
                String param = col.getColumn().replace(".", "_") + "_" + index;

                // AUTO ILIKE dari DataFilter
                if (operator.equals("ILIKE")) {
                    where.append(" AND LOWER(")
                            .append(jpqlField)
                            .append(") LIKE LOWER(:").append(param).append(")");
                } else {
                    where.append(" AND ").append(jpqlField).append(" ")
                            .append(operator).append(" :").append(param);
                }

                Object value = col.getValue();
                // Konversi string ISO ke LocalDateTime jika field tipe LocalDateTime (support
                // nested field & Z)
                try {
                    Class<?> paramType = entityClass;
                    String[] fieldParts = col.getColumn().split("\\.");
                    for (String part : fieldParts) {
                        paramType = paramType.getDeclaredField(part).getType();
                    }
                    if (paramType != null && paramType.getName().equals("java.time.LocalDateTime")
                            && value instanceof String) {
                        String strVal = (String) value;
                        if (strVal.endsWith("Z") || strVal.contains("+")) {
                            // Parse sebagai OffsetDateTime lalu ambil LocalDateTime-nya
                            value = java.time.OffsetDateTime.parse(strVal).toLocalDateTime();
                        } else {
                            value = java.time.LocalDateTime.parse(strVal);
                        }
                    }
                } catch (Exception ignore) {
                }
                paramValues.put(param, value);
                index++;
            }
        }

        // PEMBATAS (dipasang kode, bukan request): selalu di-AND dan tidak bisa dilewati oleh filter lain.
        if (dataFilters.getScopeFields() != null) {
            for (String field : dataFilters.getScopeFields()) {
                if (!isFieldExist(entityClass, field)) {
                    throw new IllegalArgumentException("Unknown scope field: " + field);
                }
            }
            if (dataFilters.getScopeIds() == null || dataFilters.getScopeIds().isEmpty()) {
                where.append(" AND 1=0");
            } else {
                String any = String.join(" OR ", dataFilters.getScopeFields().stream()
                        .map(f -> root + "." + f + " IN :scopeIds").toList());
                where.append(" AND (").append(any).append(")");
                paramValues.put("scopeIds", dataFilters.getScopeIds());
            }
        }

        // CUSTOM
        if (!StringVerification.nullOrEmpty(dataFilters.getCustom())) {
            where.append(" ").append(dataFilters.getCustom()).append(" ");
        }

        // ==========================
        // 2. GROUP BY
        // ==========================
        StringBuilder groupBy = new StringBuilder();
        if (!StringVerification.nullOrEmpty(dataFilters.getGroup())) {
            String groupField = resolveField(entityClass, dataFilters.getGroup(), root, joins, createdJoins);
            if (groupField != null) {
                groupBy.append(" GROUP BY ").append(groupField).append(" ");
            }
        }

        // ==========================
        // 3. ORDER BY
        // ==========================
        StringBuilder orderBy = new StringBuilder();
        if (!StringVerification.nullOrEmpty(dataFilters.getOrder())) {
            String orderField = resolveField(entityClass, dataFilters.getOrder(), root, joins, createdJoins);
            if (orderField != null) {
                orderBy.append(" ORDER BY ")
                        .append(orderField)
                        .append(dataFilters.isDescending() ? " DESC" : " ASC");
            }
        }

        // ==========================
        // BUILD QUERY
        // ==========================
        String finalQuery = "SELECT " + root + " " + baseQuery + joins + where + groupBy + orderBy;
        String finalCount = "SELECT COUNT(" + root + ") " + baseQuery + joins + where;

        TypedQuery<T> typedQuery = entityManager.createQuery(finalQuery, entityClass);
        TypedQuery<Long> countQuery = entityManager.createQuery(finalCount, Long.class);

        // SET PARAMS
        for (var entry : paramValues.entrySet()) {
            typedQuery.setParameter(entry.getKey(), entry.getValue());
            countQuery.setParameter(entry.getKey(), entry.getValue());
        }

        // LOCK
        if (lock) {
            typedQuery.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        }

        // PAGINATION
        int offset = StringVerification.nullOrEmpty(dataFilters.getOffset())
                ? 0
                : Integer.parseInt(dataFilters.getOffset());

        int limit = StringVerification.nullOrEmpty(dataFilters.getLimit())
                ? 20
                : Integer.parseInt(dataFilters.getLimit());

        typedQuery.setFirstResult(offset);
        typedQuery.setMaxResults(limit);

        // EXECUTE
        List<T> results = typedQuery.getResultList();
        Long total = countQuery.getSingleResult();

        DataResult<T> result = new DataResult<>();
        result.setData(results);
        result.setTotal(BigInteger.valueOf(results.size()));
        result.setGrandTotal(BigInteger.valueOf(total));

        return result;
    }

    // ============================================================
    // AUTO-JOIN untuk nested field (profile.name -> LEFT JOIN)
    // ============================================================
    private String resolveField(
            Class<?> clazz,
            String column,
            String rootAlias,
            StringBuilder joinBuilder,
            Set<String> createdJoins) {
        if (!column.contains(".")) {
            return isFieldExist(clazz, column) ? rootAlias + "." + column : null;
        }

        String[] parts = column.split("\\.");
        // Nama diteruskan mentah ke JPQL (JOIN/ORDER/GROUP), jadi hanya identifier yang boleh lewat.
        for (String part : parts) {
            if (!IDENTIFIER.matcher(part).matches()) {
                return null;
            }
        }
        String alias = rootAlias;
        Class<?> currentClass = clazz;

        for (int i = 0; i < parts.length - 1; i++) {
            String join = parts[i];
            String joinAlias = alias + "_" + join;

            if (!createdJoins.contains(joinAlias)) {
                joinBuilder.append(" LEFT JOIN ").append(alias).append(".").append(join)
                        .append(" ").append(joinAlias).append(" ");
                createdJoins.add(joinAlias);
            }

            alias = joinAlias;
            currentClass = getFieldTypeSafe(currentClass, join);
        }

        String last = parts[parts.length - 1];
        return alias + "." + last;
    }

    private boolean isFieldExist(Class<?> clazz, String name) {
        EntityType<?> meta = entityManager.getMetamodel().entity(clazz);
        return meta.getAttributes().stream().anyMatch(a -> a.getName().equals(name));
    }

    private Class<?> getFieldTypeSafe(Class<?> clazz, String field) {
        try {
            return clazz.getDeclaredField(field).getType();
        } catch (Exception e) {
            return Object.class;
        }
    }
}
