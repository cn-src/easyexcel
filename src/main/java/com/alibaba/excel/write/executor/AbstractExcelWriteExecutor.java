package com.alibaba.excel.write.executor;

import com.alibaba.excel.context.WriteContext;
import com.alibaba.excel.converters.Converter;
import com.alibaba.excel.converters.ConverterKeyBuild;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.exception.ExcelDataConvertException;
import com.alibaba.excel.metadata.CellData;
import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.property.ExcelContentProperty;
import com.alibaba.excel.util.WriteHandlerUtils;
import com.alibaba.excel.write.metadata.holder.WriteHolder;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.usermodel.Sheet;

/**
 * Excel write Executor
 *
 * @author Jiaju Zhuang
 */
public abstract class AbstractExcelWriteExecutor implements ExcelWriteExecutor {
    protected WriteContext writeContext;

    public AbstractExcelWriteExecutor(WriteContext writeContext) {
        this.writeContext = writeContext;
    }

    protected CellData<?> converterAndSet(WriteHolder currentWriteHolder, Class<?> clazz, CellDataTypeEnum targetType,
        Cell cell, Object value,
        ExcelContentProperty excelContentProperty, Head head, Integer relativeRowIndex) {
        if (value instanceof String && currentWriteHolder.globalConfiguration().getAutoTrim()) {
            value = ((String)value).trim();
        }
        CellData<?> cellData = convert(currentWriteHolder, clazz, targetType, cell, value, excelContentProperty);
        if (cellData.getFormula() != null && cellData.getFormula()) {
            cell.setCellFormula(cellData.getFormulaValue());
        }
        if (cellData.getType() == null) {
            cellData.setType(CellDataTypeEnum.EMPTY);
        }
        WriteHandlerUtils.afterCellDataConverted(writeContext, cellData, cell, head, relativeRowIndex, Boolean.FALSE);
        switch (cellData.getType()) {
            case STRING:
                cell.setCellValue(cellData.getStringValue());
                return cellData;
            case BOOLEAN:
                cell.setCellValue(cellData.getBooleanValue());
                return cellData;
            case NUMBER:
                cell.setCellValue(cellData.getNumberValue().doubleValue());
                return cellData;
            case DATE:
                cell.setCellValue(cellData.getDateValue());
                return cellData;
            case IMAGE:
                setImageValue(cellData, cell);
                return cellData;
            case EMPTY:
                return cellData;
            default:
                throw new ExcelDataConvertException(cell.getRow().getRowNum(), cell.getColumnIndex(), cellData,
                    excelContentProperty, "Not supported data:" + value + " return type:" + cell.getCellType()
                    + "at row:" + cell.getRow().getRowNum());
        }
    }

    protected CellData<?> convert(WriteHolder currentWriteHolder, Class<?> clazz, CellDataTypeEnum targetType,
        Cell cell,
        Object value, ExcelContentProperty excelContentProperty) {
        // This means that the user has defined the data.
        if (value instanceof CellData) {
            CellData<?> cellDataValue = (CellData<?>)value;
            if (cellDataValue.getType() != null) {
                return cellDataValue;
            } else {
                if (cellDataValue.getData() == null) {
                    cellDataValue.setType(CellDataTypeEnum.EMPTY);
                    return cellDataValue;
                }
            }
            CellData<?> cellDataReturn = doConvert(currentWriteHolder, cellDataValue.getData().getClass(), targetType,
                cell, cellDataValue.getData(), excelContentProperty);
            // The formula information is subject to user input
            if (cellDataValue.getFormula() != null) {
                cellDataReturn.setFormula(cellDataValue.getFormula());
                cellDataReturn.setFormulaValue(cellDataValue.getFormulaValue());
            }
            return cellDataReturn;
        }
        return doConvert(currentWriteHolder, clazz, targetType, cell, value, excelContentProperty);
    }

    private CellData<?> doConvert(WriteHolder currentWriteHolder, Class<?> clazz, CellDataTypeEnum targetType,
        Cell cell, Object value, ExcelContentProperty excelContentProperty) {
        Converter converter = null;
        if (excelContentProperty != null) {
            converter = excelContentProperty.getConverter();
        }
        if (converter == null) {
            converter = currentWriteHolder.converterMap().get(ConverterKeyBuild.buildKey(clazz, targetType));
        }
        if (converter == null) {
            throw new ExcelDataConvertException(cell.getRow().getRowNum(), cell.getColumnIndex(),
                new CellData<>(CellDataTypeEnum.EMPTY), excelContentProperty,
                "Can not find 'Converter' support class " + clazz.getSimpleName() + ".");
        }
        CellData<?> cellData;
        try {
            cellData = converter.convertToExcelData(value, excelContentProperty, currentWriteHolder);
        } catch (Exception e) {
            throw new ExcelDataConvertException(cell.getRow().getRowNum(), cell.getColumnIndex(),
                new CellData<>(CellDataTypeEnum.EMPTY), excelContentProperty,
                "Convert data:" + value + " error,at row:" + cell.getRow().getRowNum(), e);
        }
        if (cellData == null || cellData.getType() == null) {
            throw new ExcelDataConvertException(cell.getRow().getRowNum(), cell.getColumnIndex(),
                new CellData<>(CellDataTypeEnum.EMPTY), excelContentProperty,
                "Convert data:" + value + " return null,at row:" + cell.getRow().getRowNum());
        }
        return cellData;
    }

    private void setImageValue(CellData<?> cellData, Cell cell) {
        Sheet sheet = cell.getSheet();
        int index = sheet.getWorkbook().addPicture(cellData.getImageValue(), HSSFWorkbook.PICTURE_TYPE_PNG);
        Drawing drawing = sheet.getDrawingPatriarch();
        if (drawing == null) {
            drawing = sheet.createDrawingPatriarch();
        }
        CreationHelper helper = sheet.getWorkbook().getCreationHelper();
        ClientAnchor anchor = helper.createClientAnchor();
        if(cellData.getUseImagePositionProperty()) {
            anchor.setDx1(cellData.getImagePositionProperty().getDx1());
            anchor.setDx2(cellData.getImagePositionProperty().getDx2());
            anchor.setDy1(cellData.getImagePositionProperty().getDy1());
            anchor.setDy2(cellData.getImagePositionProperty().getDy2());
            anchor.setCol1(cellData.getImagePositionProperty().getCol1());
            anchor.setCol2(cellData.getImagePositionProperty().getCol2());
            anchor.setRow1(cellData.getImagePositionProperty().getRow1());
            anchor.setRow2(cellData.getImagePositionProperty().getRow2());
            anchor.setAnchorType(ClientAnchor.AnchorType.MOVE_AND_RESIZE);
        } else {
            anchor.setDx1(0);
            anchor.setDx2(0);
            anchor.setDy1(0);
            anchor.setDy2(0);
            anchor.setCol1(cell.getColumnIndex());
            anchor.setCol2(cell.getColumnIndex() + 1);
            anchor.setRow1(cell.getRowIndex());
            anchor.setRow2(cell.getRowIndex() + 1);
            anchor.setAnchorType(ClientAnchor.AnchorType.DONT_MOVE_AND_RESIZE);
        }
        drawing.createPicture(anchor, index);
    }
}
