package com.saasno.filter;

public class ColumnFilter {

	private String column;
	private Object value;
	private String operator;

	public ColumnFilter() {

	}

	public ColumnFilter(String column, String value) {
		this.column = column;
		this.value = value;
		this.operator = "=";
	}

	public ColumnFilter(String column, String value, String operator) {
		this.column = column;
		this.value = value;
		this.operator = operator;
	}

	public String getColumn() {
		return column;
	}

	public void setColumn(String column) {
		this.column = column;
	}

	public Object getValue() {
		return value;
	}

	public void setValue(Object value) {
		this.value = value;
	}

	public String getOperator() {
		return operator;
	}

	public void setOperator(String operator) {
		this.operator = operator;
	}

}
