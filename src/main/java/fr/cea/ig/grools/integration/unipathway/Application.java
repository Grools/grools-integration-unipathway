/*
 * Copyright Genoscope LABGeM
 * Contributor(s) : Jonathan MERCIER 2014
 *
 * jmercier@gneoscope.cns.fr
 *
 * This software is a computer program whose purpose is to research in genomic.
 *
 * This software is governed by the CeCILL license under French law and
 * abiding by the rules of distribution of free software.  You can  use,
 * modify and/ or redistribute the software under the terms of the CeCILL
 * license as circulated by CEA, CNRS and INRIA at the following URL
 * "http://www.cecill.info".
 *
 * As a counterpart to the access to the source code and  rights to copy,
 * modify and redistribute granted by the license, users are provided only
 * with a limited warranty  and the software's author,  the holder of the
 * economic rights,  and the successive licensors  have only  limited
 * liability.
 *
 * In this respect, the user's attention is drawn to the risks associated
 * with loading,  using,  modifying and/or developing or reproducing the
 * software by the user in light of its specific status of free software,
 * that may mean  that it is complicated to manipulate,  and  that  also
 * therefore means  that it is reserved for developers  and  experienced
 * professionals having in-depth computer knowledge. Users are therefore
 * encouraged to load and test the software's suitability as regards their
 * requirements in conditions enabling the security of their systems and/or
 * data to be ensured and,  more generally, to use and operate it in the
 * same conditions as regards security.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL license and that you accept its terms.
 */

package fr.cea.ig.grools.integration.unipathway;

import com.lexicalscope.jewel.cli.Cli;
import com.lexicalscope.jewel.cli.CliFactory;
import com.lexicalscope.jewel.JewelRuntimeException;

import fr.cea.ig.external.unipathway.client.http.UniPathway;
import fr.cea.ig.file.io.obo.OboInMemoryReader;
import fr.cea.ig.file.model.obo.UPA;
import fr.cea.ig.file.model.obo.UER;
import fr.cea.ig.file.model.obo.Term;
import fr.cea.ig.file.model.obo.Variant;
import fr.cea.ig.file.model.obo.TermRelations;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

public class Application {

    private static void pathwayWriter( final StringBuilder content, final Term term, final Class<? extends Term> untilItem ) {
        List<Variant>   variants    = new ArrayList<Variant>();
        Integer         var         = 0;
        Variant.getVariant( ( (TermRelations) term ).getChilds(), variants );
        if (!untilItem.isInstance(term) && variants.size() != 0 ) {
            for( Variant variant : variants ){
                content.append( "package fr.cea.ig.grools.pathway\n" );
                content.append( "import fr.cea.ig.grools.core.Fact\n" );
                content.append( "import fr.cea.ig.grools.core.Pathway\n\n" );
                content.append( "rule \"").append(term.getName()).append("_").append( var.toString() ).append("\" when \n");
                content.append( "not Pathway( " + term.getName() + ", Source.UNIPATHWAY, Conclusion.PRESENT )");
                for( Term t : variant ) {
                    content.append("$" + t.getName() + " : Fact( state = State.OBSERVED , name = " + t.getName() + ")");
                }
                content.append( "then\n" );
                content.append( "Pathway pathway = new Pathway( " + term.getName() + ", Source.UNIPATHWAY, Conclusion.PRESENT )");
                content.append( "end\n" );
                var++;
            }
        }
    }

    public static void main ( String [] args )  {
        final Cli<Parameters> cli = CliFactory.createCli(Parameters.class);
        Parameters params = null;
        try {
            params = CliFactory.parseArguments(Parameters.class, args);
        } catch( JewelRuntimeException e){
            System.err.println( "Error while parsing command line:" );
            System.err.println( e.getMessage( ) );
            System.err.println();
            System.err.println( cli.getHelpMessage() );
            System.exit(1);
        }

        OboInMemoryReader oboReader = null;

        try {
            oboReader = new OboInMemoryReader( params.getOboFile() );
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (IOException e) {
            System.err.println( "Error while reading obo file:" );
            System.err.println( e.getMessage( ) );
            System.err.println();
            System.err.println( cli.getHelpMessage() );
            System.exit(1);
        }

        final Term term = oboReader.getTerm( params.getPathwayId() );
        if( ! ( term instanceof UPA ) ){
            System.err.println(  "Id: " + params.getPathwayId() + " is not a pathway" );
            System.exit(1);
        }

        List<List<String>> pathwayVariants = null;

        try {
            pathwayVariants = UniPathway.getVariant(params.getPathwayId());
        } catch (Exception e) {
            System.err.println( "Error while query unipathway: " + params.getPathwayId() );
            System.err.println( e.getMessage( ) );
            System.err.println();
            System.err.println( cli.getHelpMessage() );
            System.exit(1);
        }

        OutputStreamWriter  osw     = null;
        Path                output  = null;
        try {
            Path dir    = Paths.get( params.getGroolsDir() );
            output      = dir.resolve( term.getName() + ".drl" );
            osw         = new OutputStreamWriter( new FileOutputStream( output.toString() ), Charset.forName( "US-ASCII") );
        } catch (FileNotFoundException e) {
            System.err.println( "Error while writing grools file: " + output.toString() );
            System.err.println( e.getMessage( ) );
            System.err.println();
            System.err.println( cli.getHelpMessage() );
            System.exit(1);
        }
        BufferedWriter buffer = new BufferedWriter(osw, 4096 * 5);
        StringBuilder content = new StringBuilder();
        pathwayWriter( content, term, 0, UER.class );

        try {
            buffer.write( content.toString() );
        } catch (IOException e) {
            System.err.println( "Error while closing grools file: " + output.toString());
            System.err.println( e.getMessage( ) );
            System.err.println();
            System.err.println( cli.getHelpMessage() );
            System.exit(1);
        }


        try {
            buffer.close();
        } catch (IOException e) {
            System.err.println( "Error while closing grools file: " + output.toString() );
            System.err.println( e.getMessage( ) );
            System.err.println();
            System.err.println( cli.getHelpMessage() );
            System.exit(1);
        }




    }
}
