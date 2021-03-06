package de.codesourcery.iozone;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class IOZoneFileParser
{
	protected static final class FileEntry
	{
		public final int fileSize;
		public final int[] values;

		public FileEntry(int fileSize, int[] values) {
			super();
			this.fileSize = fileSize;
			this.values = values;
		}
	}

	public static final class IOZoneReport
	{
		public final String reportName;
		public final int[] recordLengths;

		final Map<Integer,FileEntry> entries = new HashMap<>();

		public IOZoneReport(String reportName, int[] recordLengths)
		{
			this.reportName = reportName;
			this.recordLengths = recordLengths;
		}

		public FileEntry getFileEntry(int fileSize) {
			return Optional.ofNullable( entries.get( fileSize ) ).orElseThrow( () -> new RuntimeException("Unknown file size: "+fileSize ) );
		}

		public List<Integer> getFileSizes()
		{
			return entries.keySet().stream().sorted().collect( Collectors.toList() );
		}

		public void addEntry( int fileSize,int[] values) {
			if ( entries.containsKey( fileSize ) ) {
				throw new RuntimeException("Duplicate file-size: "+fileSize);
			}
			entries.put( fileSize , new FileEntry( fileSize, values ) );
		}
	}

	public static final class CountingReader implements Iterator<Row>
	{
		private final List<Row> rows;
		public int currentRow = 0;

		public CountingReader(CsvReader reader)
		{
			this.rows = StreamSupport.stream( reader.spliterator() , false ).collect( Collectors.toList() );
		}

		public int previousRow() {
			return currentRow-1;
		}

		@Override
		public Row next() {
			Row result = rows.get( currentRow );
			currentRow++;
			return result;
		}

		public void pushBack() {
			currentRow--;
		}

		@Override
		public boolean hasNext()
		{
			return currentRow < rows.size();
		}
	};

	public static final class IOZoneReader implements Iterable<IOZoneReport>
	{
		private final List<IOZoneReport> reports = new ArrayList<>();

		public IOZoneReader(CsvReader reader)
		{
			final CountingReader counter = new CountingReader(reader);
			try {
				for ( CountingReader it = new CountingReader(reader) ; it.hasNext() ; )
				{
					Row row = it.next();
					if ( row.getCellCount() != 1 )
					{
						throw new RuntimeException("Expected row with one cell @ row "+counter.previousRow()+" but got "+row);
					}
					final String reportName = row.iterator().next().getStringValue();
					System.out.println("Report: "+reportName);

					row = it.next();
					final int[] recordLens = row.cellStream().mapToInt( Cell::getIntValue ).toArray();
					final IOZoneReport report = new IOZoneReport( reportName , recordLens );

					row = it.next();
					int cellsPerRow = -1;
					do
					{
						final int fileSize = row.cell(0).getIntValue();
						System.out.println("File size: "+fileSize+"k");
						report.addEntry( fileSize , row.cellStream().skip( 1 ).mapToInt( Cell::getIntValue ).toArray() );
						cellsPerRow = row.getCellCount();
						if ( ! it.hasNext() ) {
							break;
						}
						row = it.next();
						if ( row.getCellCount() < cellsPerRow )
						{
							it.pushBack();
							break;
						}
					} while ( true );

					if ( reports.stream().anyMatch( r -> r.reportName.equals( report.reportName ) ) ) {
						throw new RuntimeException("Duplicate report "+report.reportName);
					}
					reports.add( report );
				}
			} catch(Exception e) {
				throw new RuntimeException("At row: "+counter.previousRow()+" : "+e.getMessage() ,e );
			}
		}
		
		public IOZoneReport getReport(String name) {
		    return stream().filter( r -> name.equals( r.reportName) ).findFirst().orElseThrow( () -> new RuntimeException("Missing report: '"+name+"'" ) );
		}
		
		public List<IOZoneReport> getReports() {
            return reports;
        }

		public Stream<IOZoneReport> stream() {
			return reports.stream();
		}

		@Override
		public Iterator<IOZoneReport> iterator() {
			return reports.iterator();
		}
	}

	public static final class Cell
	{
		private String value;

		public Cell(String value) {
			this.value = value;
		}

		public String getStringValue() {
			return value;
		}

		public int getIntValue() {
			return Integer.parseInt( value );
		}

		@Override
		public String toString() {
			return value;
		}
	}

	public static final class Row implements Iterable<Cell>
	{
		private final List<Cell> cells = new ArrayList<>();

		public Row() {
		}

		public void addCell(Cell cell) {
			this.cells.add( cell );
		}

		@Override
		public String toString() {
			return cells.stream().map( Cell::getStringValue ).collect(Collectors.joining("," ) );
		}

		public Cell cell(int idx) {
			return cells.get(idx);
		}

		public int getCellCount() {
			return cells.size();
		}

		public Stream<Cell> cellStream() {
			return cells.stream();
		}

		@Override
		public Iterator<Cell> iterator() {
			return cells.iterator();
		}
	}

	public static final class CsvReader implements Iterable<Row>
	{
		private final BufferedReader reader;
		private final List<Row> rows = new ArrayList<>();

		public CsvReader(InputStream in) throws IOException
		{
			reader = new BufferedReader( new InputStreamReader(in ) );

			String line = null;
			while ( ( line = reader.readLine() ) != null )
			{
				if ( line.trim().length() == 0 ) {
					continue;
				}
				final String[] cells = split( line );
				final Row row = new Row();
				rows.add( row );
				for ( String cellValue : cells )
				{
					if ( cellValue.startsWith("\"") && cellValue.endsWith( "\"" ) ) {
						cellValue = cellValue.substring( 1 , cellValue.length()-1 );
					}
					row.addCell( new Cell( cellValue ) );
				}
			}
		}

		private String[] split(String input)
		{
			final List<String> result = new ArrayList<>();
			boolean escaped = false;
			final StringBuffer buffer = new StringBuffer();
			for ( int i = 0 ; i < input.length() ; i++ )
			{
				final char c = input.charAt( i );
				if ( c == ' ' )
				{
					if ( ! escaped )
					{
						if ( buffer.toString().trim().length() > 0 )
						{
							result.add( buffer.toString() );
						}
						buffer.setLength( 0 );
						continue;
					}
				}
				if (c == '"' ) {
					escaped = ! escaped;
				}
				buffer.append( c );
			}
			if ( buffer.toString().trim().length() > 0 )
			{
				result.add( buffer.toString() );
			}
			return result.toArray( new String[ result.size() ] );
		}

		public boolean isEmpty() {
			return rows.isEmpty();
		}

		public int getRowCount() {
			return rows.size();
		}

		@Override
		public Iterator<Row> iterator() {
			return rows.iterator();
		}
	}
}