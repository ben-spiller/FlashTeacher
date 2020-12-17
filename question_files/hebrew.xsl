<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

<xsl:strip-space elements="node"/>

<!-- This is a custom XSL file that transforms an XML file into a printable HTML summary of the vocabulary. -->

<xsl:template match="/questionList">

	<html>
		<head>
			<style type="text/css">
				p, h1                   {font-family: tahoma, sans-serif}
				p, h1                   {text-align: center}

				td                      {white-space-collapse:collapse; border-bottom-style:solid; padding-top: 0.1cm; padding-bottom: 0.1cm; border-width:1; border-color:black}

				td#question             {font-family: tahoma, sans-serif}
				td#answer               {font-family: "SBL Hebrew", sans-serif; font-size: 200%; direction: rtl}
				td#question, td#answer  {padding-left: 0.5cm; padding-right: 0.5cm}
				td#question             {border-left-style:solid}

				td#empty                {padding-left: 1cm}

				/* Emphasise tricky parts of an answer */
				em                      {white-space-collapse:collapse; font-size: 120%; font-weight:bold; font-style:normal}

				/* Pink for boys, blue for girls, grey for common */
				td#answer.gender-f      {color: #FF00FF}
				td#answer.gender-m      {color: RoyalBlue}
				td#answer.gender-c      {color: #4AA02C}

				td#answer.verb          {color: #CC6600}

				table                   {empty-cells:show; border-collapse: separate}

			</style>

			<title><xsl:value-of select="/questionList/@title"/></title>
		</head>

		<body>
			<h1><xsl:value-of select="/questionList/@title"/></h1>

			<xsl:apply-templates select="/questionList/@author"/> 

			<table> <!--  border="1" cellpadding="7" frame="hsides" rules="all"-->

				<!-- Have a couple of questions per row, so we use the page space well -->
				<xsl:for-each select="/questionList/question[position() mod 2 = 1]">
					<tr>
						<xsl:apply-templates select="."/> 
						<td id="empty"></td> <!-- non-breaking space &#160;x -->
						<xsl:apply-templates select="following-sibling::question[1]"/> 

					</tr>
				</xsl:for-each>
			</table>
		</body>
	</html>
</xsl:template>

<!-- Handle each distinct question here -->
<xsl:template match="/questionList/question">
	<td id="answer" class="{@class}" align="right">
		<xsl:apply-templates select="answerText"> 
			<!-- xsl:sort order="descending"/ -->
		</xsl:apply-templates>
	</td>
	<td id="question" class="{@class}" align="left">
		<xsl:apply-templates select="questionText"/> 
	</td>
</xsl:template>

<xsl:template match="answerText/text()">
	<xsl:value-of select="normalize-space(.)"/>
</xsl:template>

<!-- Emphasis tags witin answers -->
<xsl:template match="answerText/em">
	<xsl:copy-of select="."/>
</xsl:template>

<!-- Print author paragraph, if it exists -->
<xsl:template match="/questionList/@author">
	<p>
		Created by: <xsl:value-of select="."/>
	</p>
</xsl:template>

</xsl:stylesheet>
