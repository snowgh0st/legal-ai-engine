#!/usr/bin/env python3
"""
Synthetic Legal Contract Generator for GraphRAG Testing
Creates a realistic 2-page Master Services Agreement (PDF).
"""

from reportlab.lib.pagesizes import letter
from reportlab.lib.units import inch
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, HRFlowable

def create_sample_contract(filename="sample_contract.pdf"):
    doc = SimpleDocTemplate(
        filename,
        pagesize=letter,
        rightMargin=0.75 * inch,
        leftMargin=0.75 * inch,
        topMargin=0.75 * inch,
        bottomMargin=0.75 * inch
    )

    styles = getSampleStyleSheet()

    # Custom typography styles
    title_style = ParagraphStyle(
        'ContractTitle',
        parent=styles['Title'],
        fontName='Helvetica-Bold',
        fontSize=16,
        leading=20,
        alignment=1, # Centered
        spaceAfter=15
    )
    
    heading_style = ParagraphStyle(
        'SectionHeading',
        parent=styles['Heading2'],
        fontName='Helvetica-Bold',
        fontSize=11,
        leading=14,
        spaceBefore=12,
        spaceAfter=6
    )

    body_style = ParagraphStyle(
        'ContractBody',
        parent=styles['Normal'],
        fontName='Times-Roman',
        fontSize=10,
        leading=14,
        spaceAfter=8
    )

    story = []

    # Document Header
    story.append(Paragraph("MASTER SERVICES AGREEMENT", title_style))
    story.append(HRFlowable(width="100%", thickness=1, spaceAfter=15))

    # Preamble
    preamble_text = (
        "This Master Services Agreement (\"Agreement\") is entered into as of <b>January 15, 2025</b> "
        "(\"Effective Date\"), by and between <b>Apex Global Technologies Inc.</b>, a Delaware corporation "
        "having its principal place of business at 100 Enterprise Way, Wilmington, DE 19801 (\"Client\"), "
        "and <b>Vertex AI Solutions LLC</b>, a New York limited liability company having its principal place of business "
        "at 500 Innovation Ave, New York, NY 10001 (\"Provider\")."
    )
    story.append(Paragraph(preamble_text, body_style))
    story.append(Spacer(1, 10))

    # Section 1: Scope of Services
    story.append(Paragraph("SECTION 1: SCOPE OF SERVICES", heading_style))
    sec1_text = (
        "Provider agrees to perform enterprise software development, data pipeline engineering, and machine learning "
        "infrastructure management services as specified in Statements of Work (\"SOW\") executed under this Agreement. "
        "This Agreement shall expire on <b>January 15, 2027</b>, unless terminated earlier in accordance with Section 3."
    )
    story.append(Paragraph(sec1_text, body_style))

    # Section 2: Confidentiality & Data Protection
    story.append(Paragraph("SECTION 2: CONFIDENTIALITY AND OBLIGATIONS", heading_style))
    sec2_text = (
        "Each Party acknowledges that during the Term, it may receive confidential or proprietary information of the other Party "
        "(\"Confidential Information\"). The receiving Party shall protect such information with the same degree of care it uses "
        "for its own confidential materials, but no less than reasonable care. Confidential Information shall not include information "
        "that is publicly known through no fault of the receiving Party."
    )
    story.append(Paragraph(sec2_text, body_style))

    # Section 3: Termination & Cancellation
    story.append(Paragraph("SECTION 3: TERMINATION AND DEPENDENCIES", heading_style))
    sec3_text = (
        "Either Party may terminate this Agreement without cause upon <b>30 days written notice</b> to the other Party. "
        "In the event of a material breach of Section 2 (Confidentiality) or Section 4 (Indemnification), the non-breaching Party "
        "may terminate this Agreement immediately. Termination under Section 3 triggers immediate data destruction obligations under Section 2."
    )
    story.append(Paragraph(sec3_text, body_style))

    # Section 4: Indemnification & Liability
    story.append(Paragraph("SECTION 4: INDEMNIFICATION AND LIMITATION OF LIABILITY", heading_style))
    sec4_text = (
        "Provider agrees to defend, indemnify, and hold harmless Client against any third-party claims, losses, or damages "
        "arising out of Provider's gross negligence, willful misconduct, or infringement of intellectual property. "
        "NEITHER PARTY SHALL BE LIABLE TO THE OTHER FOR SPECIAL, INDIRECT, OR CONSEQUENTIAL DAMAGES ARISING OUT OF THIS AGREEMENT."
    )
    story.append(Paragraph(sec4_text, body_style))

    # Section 5: Governing Law
    story.append(Paragraph("SECTION 5: GOVERNING LAW AND JURISDICTION", heading_style))
    sec5_text = (
        "This Agreement shall be governed by, construed, and enforced in accordance with the laws of the <b>State of New York</b>, "
        "without giving effect to any conflict of laws principles. Any legal suit or proceeding shall be brought exclusively "
        "in the state or federal courts located in New York County, New York."
    )
    story.append(Paragraph(sec5_text, body_style))

    story.append(Spacer(1, 20))

    # Signatures
    sig_text = (
        "IN WITNESS WHEREOF, the Parties have executed this Master Services Agreement as of the Effective Date.<br/><br/>"
        "<b>APEX GLOBAL TECHNOLOGIES INC.</b>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<b>VERTEX AI SOLUTIONS LLC</b><br/>"
        "By: ___________________________&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;By: ___________________________<br/>"
        "Name: John Doe&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Name: Jane Smith<br/>"
        "Title: Chief Executive Officer&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Title: Managing Director"
    )
    story.append(Paragraph(sig_text, body_style))

    # Build PDF
    doc.build(story)
    print(f"Successfully generated synthetic contract: {filename}")

if __name__ == "__main__":
    create_sample_contract()