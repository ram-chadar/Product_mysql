package com.jbk.product.service;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import com.jbk.product.dao.ProductDao;
import com.jbk.product.entity.Product;
import com.jbk.product.exception.GlobalExceptionHandler;
import com.jbk.product.sort.ProductIdComparator;
import com.jbk.product.sort.ProductNameComparator;
import com.jbk.product.validataion.ValidateObject;

@Service
public class ProductServiceImpl implements ProductService {

	@Autowired
	private ProductDao dao;

	String excludedRows = "";
	int totalRecordCount = 0;
	Map<String, Object> map = new HashMap<String, Object>();
	Map<String, String> validatedError = new HashMap<String, String>();
	Map<Integer, Map<String, String>> errorMap = new HashMap<Integer, Map<String, String>>();

	@Override
	public boolean saveProduct(Product product) {
		String id = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new java.util.Date());
		product.setProductId(id);
		boolean isAdded = dao.saveProduct(product);

		return isAdded;
	}

	@Override
	public List<Product> getAllProduct() {
		List<Product> list = dao.getAllProduct();
		return list;
	}

	@Override
	public Product getProductById(String productId) {
		Product product = dao.getProductById(productId);
		return product;
	}

	@Override
	public boolean deleteProduct(String productId) {
		boolean isDeleted = dao.deleteProduct(productId);
		return isDeleted;
	}

	@Override
	public boolean updateProduct(Product product) {
		boolean isUpdated = dao.updateProduct(product);
		return isUpdated;
	}

	@Override
	public List<Product> sortProducts(String sortBy) {
		List<Product> allProducts = getAllProduct();

		if (allProducts.size() > 1) {

			if (sortBy.equalsIgnoreCase("productId")) {
				Collections.sort(allProducts, new ProductIdComparator());

			} else if (sortBy.equalsIgnoreCase("productName")) {
				Collections.sort(allProducts, new ProductNameComparator());
			}
		}
		return allProducts;
	}

	@Override
	public Product getMaxPriceProducts() { // by using core java >> first get max ( Projections.max("productPrice"))
											// >> second Restrictions.eq("productPrice",maxValue)
		List<Product> allProduct = getAllProduct();
		Product product = null;
		if (!allProduct.isEmpty()) {
			product = allProduct.stream().max(Comparator.comparingDouble(Product::getProductPrice)).get();
		}
		return product;
	}

	@Override
	public double sumOfProductPrice() { // by using core java >> Projection.sum("productPrice")
		List<Product> allProduct = getAllProduct();

		double sum = 0;
		if (!allProduct.isEmpty()) {
			sum = allProduct.stream().mapToDouble(Product::getProductPrice).sum();
		}
		return sum;
	}

	@Override
	public int getTotalCountOfProducts() { // by using core java >> Projections.rowCount(;)
		List<Product> allProduct = getAllProduct();
		int size = 0;
		if (!allProduct.isEmpty()) {
			size = allProduct.size();
		}
		return size;
	}

	public List<Product> readExcel(String filePath) {
		Workbook workbook = null;
		FileInputStream fis = null;
		List<Product> list = new ArrayList<Product>();
		Product product = null;

		try {
			fis = new FileInputStream(new File(filePath));
			workbook = new XSSFWorkbook(fis);

			Sheet sheet = workbook.getSheetAt(1);
			totalRecordCount = sheet.getLastRowNum();
			Iterator<Row> rows = sheet.rowIterator();
			int rowCount = 0;

			while (rows.hasNext()) {

				Row row = rows.next();
				if (rowCount == 0) {
					rowCount++;
					continue;
				}
				product = new Product();
				Thread.sleep(1);
				String id = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new java.util.Date());
				product.setProductId(id);
				Iterator<Cell> cells = row.cellIterator();

				while (cells.hasNext()) {
					Cell cell = cells.next();

					int column = cell.getColumnIndex();

					switch (column) {
					case 0: {
						product.setProductName(cell.getStringCellValue());
						break;
					}
					case 1: {
						product.setProductQty((int) cell.getNumericCellValue());
						break;
					}
					case 2: {
						product.setProductPrice((int) cell.getNumericCellValue());
						break;
					}
					case 3: {
						product.setProductType(cell.getStringCellValue());
						break;
					}

					}

				}
				validatedError = ValidateObject.validateProduct(product);
				if (validatedError == null || validatedError.isEmpty()) {
					list.add(product);

				} else {
					int rowNum = row.getRowNum() + 1;
					// excludedRows = excludedRows + rowNum + ",";

					errorMap.put(rowNum, validatedError);

				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (workbook != null)
					workbook.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		return list;

	}

	@Override
	public Map<String, Object> uploadSheet(CommonsMultipartFile file, HttpSession httpSession) {

		String path = httpSession.getServletContext().getRealPath("/");
		String fileName = file.getOriginalFilename();

		int [] arr;

		FileOutputStream fos = null;
		byte[] data = file.getBytes();
		try {
			System.out.println(path);
			fos = new FileOutputStream(new File(path + File.separator + fileName));
			fos.write(data);

			List<Product> list = readExcel(path + File.separator + fileName);

			arr = dao.uploadProductList(list);

			map.put("Total Record In Sheet", totalRecordCount);
			map.put("Uploaded Records In DB", arr[0]);
			map.put("Exists Records In DB", arr[1]);
			map.put("Total Excluded ", errorMap.size());
			map.put("Bad Record Row Number", errorMap);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return map;
	}

	@Override
	public String exportToExcel(HttpSession session) {
		List<Product> allProduct = getAllProduct();
		String filePath = null;
		String[] columns = { "ID", "NAME", "QTY", "PRICE", "TYPE" };
		try {

			// Create a Workbook
			Workbook workbook = new XSSFWorkbook(); // new HSSFWorkbook() for generating `.xls` file

			/*
			 * CreationHelper helps us create instances of various things like DataFormat,
			 * Hyperlink, RichTextString etc, in a format (HSSF, XSSF) independent way
			 */

			// Create a Sheet
			Sheet sheet = workbook.createSheet("product");

			// Create a Font for styling header cells
			Font headerFont = workbook.createFont();
			headerFont.setBold(true);
			headerFont.setFontHeightInPoints((short) 14);
			headerFont.setColor(IndexedColors.RED.getIndex());

			// Create a CellStyle with the font
			CellStyle headerCellStyle = workbook.createCellStyle();
			headerCellStyle.setFont(headerFont);

			// Create a Row
			Row headerRow = sheet.createRow(0);

			// Create cells
			for (int i = 0; i < columns.length; i++) {
				Cell cell = headerRow.createCell(i);
				cell.setCellValue(columns[i]);
				cell.setCellStyle(headerCellStyle);
			}

			

			// Create Other rows and cells with products data
			int rowNum = 1;
			for (Product product : allProduct) {
				Row row = sheet.createRow(rowNum++);

				row.createCell(0).setCellValue(product.getProductId());

				row.createCell(1).setCellValue(product.getProductName());

				row.createCell(2).setCellValue(product.getProductQty());

				row.createCell(3).setCellValue(product.getProductPrice());

				row.createCell(4).setCellValue(product.getProductType());
			}

			// Resize all columns to fit the content size
			for (int i = 0; i < columns.length; i++) {
				sheet.autoSizeColumn(i);
			}

			// Write the output to a file
			//path = session.getServletContext().getRealPath("/exported");
			 filePath = System.getProperty("user.home");
			filePath=filePath+"/Downloads";
			
			FileOutputStream fileOut = new FileOutputStream(filePath + File.separator + "product.xlsx");
			workbook.write(fileOut);
			fileOut.close();

			// Closing the workbook
			workbook.close();

		} catch (Exception e) {
			e.printStackTrace();
		}
		return filePath + File.separator + "product.xlsx";
	}

}
