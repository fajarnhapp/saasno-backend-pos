package com.saasno.repository;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import com.saasno.entity.LogEntity;
import com.saasno.filter.ColumnFilter;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.verification.StringVerification;

/**
 * Audit log disimpan di MongoDB. Filter mengikuti perilaku DataTable (JPA) supaya
 * endpoint log dan export tidak berubah; filter "custom" (JPQL) tidak didukung di sini.
 */
@Repository
public class LogRepository {

	private final MongoTemplate mongoTemplate;

	public LogRepository(MongoTemplate mongoTemplate) {
		this.mongoTemplate = mongoTemplate;
	}

	public LogEntity store(LogEntity entity) {
		return mongoTemplate.save(entity);
	}

	public DataResult<LogEntity> dataTable(DataFilter dataFilters) {
		List<Criteria> criterias = new ArrayList<>();

		if (dataFilters.getColumn() != null) {
			for (ColumnFilter col : dataFilters.getColumn()) {
				Class<?> type = fieldType(col.getColumn());
				if (type == null || col.getValue() == null) {
					continue;
				}
				Criteria criteria = toCriteria(col, type);
				if (criteria != null) {
					criterias.add(criteria);
				}
			}
		}

		Query query = criterias.isEmpty() ? new Query()
				: new Query(new Criteria().andOperator(criterias.toArray(new Criteria[0])));

		long total = mongoTemplate.count(Query.of(query), LogEntity.class);

		if (!StringVerification.nullOrEmpty(dataFilters.getOrder()) && fieldType(dataFilters.getOrder()) != null) {
			query.with(Sort.by(dataFilters.isDescending() ? Sort.Direction.DESC : Sort.Direction.ASC,
					dataFilters.getOrder()));
		}

		int offset = StringVerification.nullOrEmpty(dataFilters.getOffset()) ? 0
				: Integer.parseInt(dataFilters.getOffset());
		int limit = StringVerification.nullOrEmpty(dataFilters.getLimit()) ? 20
				: Integer.parseInt(dataFilters.getLimit());
		query.skip(offset).limit(limit);

		List<LogEntity> results = mongoTemplate.find(query, LogEntity.class);

		DataResult<LogEntity> result = new DataResult<>();
		result.setData(results);
		result.setTotal(BigInteger.valueOf(results.size()));
		result.setGrandTotal(BigInteger.valueOf(total));
		return result;
	}

	private Criteria toCriteria(ColumnFilter col, Class<?> type) {
		String operator = col.getOperator().toUpperCase();
		Criteria criteria = Criteria.where(col.getColumn());

		if (operator.equals("ILIKE")) {
			String value = col.getValue().toString();
			if (type == String.class) {
				return criteria.regex(likeToRegex(value), "i");
			}
			// Kolom angka tidak bisa di-regex, jadi dicocokkan persis.
			Object number = convert(value.replace("%", ""), type);
			return number == null ? null : criteria.is(number);
		}

		Object value = convert(col.getValue(), type);
		if (value == null) {
			return null;
		}
		return switch (operator) {
			case ">=" -> criteria.gte(value);
			case "<=" -> criteria.lte(value);
			case ">" -> criteria.gt(value);
			case "<" -> criteria.lt(value);
			case "!=", "<>" -> criteria.ne(value);
			default -> criteria.is(value);
		};
	}

	private String likeToRegex(String like) {
		StringBuilder regex = new StringBuilder("^");
		for (String part : like.split("%", -1)) {
			if (regex.length() > 1) {
				regex.append(".*");
			}
			regex.append(Pattern.quote(part));
		}
		return regex.append("$").toString();
	}

	private Object convert(Object value, Class<?> type) {
		if (!(value instanceof String str)) {
			return value;
		}
		try {
			if (type == LocalDateTime.class) {
				return str.endsWith("Z") || str.contains("+") ? OffsetDateTime.parse(str).toLocalDateTime()
						: LocalDateTime.parse(str);
			}
			if (type == int.class || type == Integer.class) {
				return Integer.parseInt(str.trim());
			}
			if (type == long.class || type == Long.class) {
				return Long.parseLong(str.trim());
			}
			return str;
		} catch (Exception e) {
			return null;
		}
	}

	private Class<?> fieldType(String name) {
		try {
			return LogEntity.class.getDeclaredField(name).getType();
		} catch (NoSuchFieldException e) {
			return null;
		}
	}
}
