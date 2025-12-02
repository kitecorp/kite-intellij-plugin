package cloud.kitelang.intellij.formatter;

import cloud.kitelang.intellij.parser.KiteParserDefinition;
import cloud.kitelang.intellij.psi.KiteElementTypes;
import cloud.kitelang.intellij.psi.KiteTokenTypes;
import com.intellij.formatting.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.TokenType;
import com.intellij.psi.formatter.common.AbstractBlock;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a formatting block in the Kite language structure.
 * Handles indentation, spacing, and child block creation.
 */
public class KiteBlock extends AbstractBlock {
    private final SpacingBuilder spacingBuilder;
    private final Indent indent;
    private final Integer alignmentPadding; // For object property alignment
    // Store schema alignment info at instance level for use during block building
    private SchemaAlignmentInfo schemaAlignmentInfo = null;

    public KiteBlock(@NotNull ASTNode node,
                     @Nullable Wrap wrap,
                     @Nullable Alignment alignment,
                     @NotNull Indent indent,
                     @NotNull SpacingBuilder spacingBuilder) {
        this(node, wrap, alignment, indent, spacingBuilder, null);
    }

    public KiteBlock(@NotNull ASTNode node,
                     @Nullable Wrap wrap,
                     @Nullable Alignment alignment,
                     @NotNull Indent indent,
                     @NotNull SpacingBuilder spacingBuilder,
                     @Nullable Integer alignmentPadding) {
        super(node, wrap, alignment);
        this.spacingBuilder = spacingBuilder;
        this.indent = indent;
        this.alignmentPadding = alignmentPadding;

    }

    @Override
    protected List<Block> buildChildren() {
        IElementType nodeType = myNode.getElementType();

        // Special handling for object literals - align colons only for multi-line objects
        if (nodeType == KiteElementTypes.OBJECT_LITERAL) {
            if (isMultiLine(myNode)) {
                return buildAlignedChildren(KiteTokenTypes.COLON);
            }
            // Single-line objects: no alignment, use default block building
        }

        // Array literals - use default block building
        if (nodeType == KiteElementTypes.ARRAY_LITERAL) {
            // Will use default block building below
        }

        // Align equals in resource/component/schema blocks
        if (nodeType == KiteElementTypes.RESOURCE_DECLARATION ||
            nodeType == KiteElementTypes.COMPONENT_DECLARATION ||
            nodeType == KiteElementTypes.SCHEMA_DECLARATION) {
            return buildAlignedChildren(KiteTokenTypes.ASSIGN);
        }

        // FILE level: handle both decorator colons and declaration equals
        if (nodeType == KiteParserDefinition.FILE) {
            return buildFileChildren();
        }

        // Default block building for other elements (including ARRAY_LITERAL)
        List<Block> blocks = new ArrayList<>();
        ASTNode child = myNode.getFirstChildNode();
        int braceDepth = 0;  // Track nesting depth instead of boolean
        boolean insideParens = false;
        boolean insideBrackets = false;  // Track array brackets separately
        // Track nested object literals

        while (child != null) {
            IElementType childType = child.getElementType();

            if (shouldSkipToken(childType) && child.getTextLength() > 0) {
                boolean insideBraces = braceDepth > 0;  // Compute from depth
                Indent childIndent = getChildIndent(childType, insideBraces, insideParens, insideBrackets, braceDepth);

                blocks.add(new KiteBlock(
                        child,
                        null,
                        null,
                        childIndent,
                        spacingBuilder
                ));

                // Update brace depth AFTER processing current element
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                }

                // Track brackets separately from braces
                if (childType == KiteTokenTypes.LBRACK) {
                    insideBrackets = true;
                } else if (childType == KiteTokenTypes.RBRACK) {
                    insideBrackets = false;
                }

                // Update insideParens state AFTER processing current element
                if (childType == KiteTokenTypes.LPAREN) {
                    insideParens = true;
                } else if (childType == KiteTokenTypes.RPAREN) {
                    insideParens = false;
                }
            }

            child = child.getTreeNext();
        }

        return blocks;
    }

    /**
     * Checks if a node spans multiple lines.
     * Used to determine if alignment should be applied.
     */
    private boolean isMultiLine(ASTNode node) {
        String text = node.getText();
        return text.contains("\n");
    }

    /**
     * Checks if we should skip creating a block for this token type.
     * We skip whitespace and newlines as the formatter handles them internally.
     */
    private boolean shouldSkipToken(IElementType type) {
        return type != TokenType.WHITE_SPACE &&
               type != KiteTokenTypes.NL &&
               type != KiteTokenTypes.NEWLINE;
    }

    /**
     * Checks if this node contains parenthesized named arguments (identifier : value).
     * This is used for decorator arguments like @validate(regex: "...", flag: 'i')
     */
    private boolean containsParenthesizedNamedArgs() {
        // Look for pattern: LPAREN ... IDENTIFIER ... COLON ... RPAREN
        boolean hasLParen = false;
        boolean hasIdentifier = false;
        boolean hasColon = false;

        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            IElementType type = child.getElementType();

            if (type == KiteTokenTypes.LPAREN) {
                hasLParen = true;
            } else if (hasLParen && type == KiteTokenTypes.IDENTIFIER) {
                // Check if this identifier is followed by a colon
                if (findNextToken(child, KiteTokenTypes.COLON) != null) {
                    hasIdentifier = true;
                }
            } else if (hasLParen && hasIdentifier && type == KiteTokenTypes.COLON) {
                hasColon = true;
            } else if (type == KiteTokenTypes.RPAREN && hasColon) {
                return true; // Found the pattern
            }

            child = child.getTreeNext();
        }

        return false;
    }

    /**
     * Builds children with aligned tokens (colons or equals).
     * Used for object literals, parenthesized named arguments, and block declarations.
     * Creates blocks for all tokens, calculating padding for alignment.
     * Groups consecutive similar declarations and aligns within each group.
     * <p>
     * For schema declarations, uses two-phase alignment:
     * 1. Align property names (padding after type identifiers)
     * 2. Align '=' signs (padding after property names)
     *
     * @param alignToken The token type to align (COLON or ASSIGN)
     */
    private List<Block> buildAlignedChildren(IElementType alignToken) {
        List<Block> blocks = new ArrayList<>();

        // For schema declarations, use two-phase alignment
        if (myNode.getElementType() == KiteElementTypes.SCHEMA_DECLARATION) {
            schemaAlignmentInfo = collectSchemaAlignmentInfo();
            return buildSchemaAlignedChildren();
        }

        // First pass: identify alignment groups and their max key lengths
        List<AlignmentGroup> groups = identifyAlignmentGroups(alignToken);

        // Second pass: build blocks with appropriate padding
        ASTNode child = myNode.getFirstChildNode();
        ASTNode previousIdentifier = null;
        int braceDepth = 0;  // Track nesting depth instead of boolean
        boolean insideParens = false;
        boolean insideBrackets = false;  // Track array brackets separately

        while (child != null) {
            IElementType childType = child.getElementType();

            if (shouldSkipToken(childType) && child.getTextLength() > 0) {
                boolean insideBraces = braceDepth > 0;  // Compute from depth
                Indent childIndent = getChildIndent(childType, insideBraces, insideParens, insideBrackets, braceDepth);

                // For declaration elements, inline their children instead of creating a block for the element
                if (childType == KiteElementTypes.INPUT_DECLARATION ||
                    childType == KiteElementTypes.OUTPUT_DECLARATION ||
                    childType == KiteElementTypes.VARIABLE_DECLARATION) {

                    // Build blocks for the declaration's children
                    buildDeclarationBlocks(child, alignToken, groups, blocks, insideBraces, insideParens, insideBrackets, braceDepth);
                }
                // For OBJECT_LITERAL and ARRAY_LITERAL, inline their children to fix indentation
                // This is critical: if we create a block for the literal, its children's indent
                // is calculated relative to where '{' appears, not the logical indentation level.
                // By inlining, children's indent is relative to the parent's (RESOURCE_DECLARATION's) baseline.
                else if (childType == KiteElementTypes.OBJECT_LITERAL) {
                    if (isMultiLine(child)) {
                        // Multi-line object: apply colon alignment
                        inlineObjectWithAlignment(child, blocks, insideBraces, insideParens, insideBrackets, braceDepth);
                    } else {
                        // Single-line object: no alignment
                        inlineLiteralChildren(child, blocks, insideBraces, insideParens, insideBrackets, braceDepth);
                    }
                } else if (childType == KiteElementTypes.ARRAY_LITERAL) {
                    inlineLiteralChildren(child, blocks, insideBraces, insideParens, insideBrackets, braceDepth);
                } else {
                    // Track identifiers that precede the align token
                    if (childType == KiteTokenTypes.IDENTIFIER) {
                        ASTNode nextToken = findNextToken(child, alignToken);
                        if (nextToken != null) {
                            previousIdentifier = child;
                        }
                    }

                    // Calculate padding for the align token
                    Integer padding = null;
                    if (childType == alignToken && previousIdentifier != null) {
                        // Find which group this identifier belongs to
                        AlignmentGroup group = findGroupForIdentifier(groups, previousIdentifier);
                        if (group != null) {
                            int keyLength = previousIdentifier.getTextLength();
                            // For ASSIGN, always have at least 1 space; for COLON, longest key has 0 spaces
                            int extraSpace = (alignToken == KiteTokenTypes.ASSIGN) ? 1 : 0;
                            padding = group.maxKeyLength - keyLength + extraSpace;
                        }
                        previousIdentifier = null;
                    }

                    blocks.add(new KiteBlock(
                            child,
                            null,
                            null,
                            childIndent,
                            spacingBuilder,
                            padding
                    ));
                }

                // Update brace depth AFTER processing current element
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                }

                // Track brackets separately from braces
                if (childType == KiteTokenTypes.LBRACK) {
                    insideBrackets = true;
                } else if (childType == KiteTokenTypes.RBRACK) {
                    insideBrackets = false;
                }

                // Update insideParens state AFTER processing current element
                if (childType == KiteTokenTypes.LPAREN) {
                    insideParens = true;
                } else if (childType == KiteTokenTypes.RPAREN) {
                    insideParens = false;
                }
            }

            child = child.getTreeNext();
        }

        return blocks;
    }

    /**
     * Builds children for schema declarations with two-phase alignment:
     * 1. Padding after type to align property names
     * 2. Padding after property name to align '=' signs
     * <p>
     * Uses index-based tracking instead of object identity for robustness.
     */
    private List<Block> buildSchemaAlignedChildren() {
        List<Block> blocks = new ArrayList<>();

        // Pass 1: Collect alignment info - track indices and lengths
        // We track "type tokens" which can be IDENTIFIER or built-in type keywords (like ANY)
        int maxTypeLength = 0;
        int maxPropertyLength = 0;
        List<Integer> typeIndices = new ArrayList<>();      // Index of type tokens in iteration order
        List<Integer> propertyIndices = new ArrayList<>();  // Index of property identifiers
        List<Integer> typeLengths = new ArrayList<>();      // Visual length of each type (including [])
        List<Integer> propertyLengths = new ArrayList<>();  // Length of each property name

        ASTNode child = myNode.getFirstChildNode();
        int currentTypeIndex = -1;
        int currentTypeLength = 0;
        boolean inSchemaBody = false;
        int typeTokenIndex = 0;  // Count type tokens (IDENTIFIER or built-in type keywords)

        while (child != null) {
            IElementType childType = child.getElementType();

            if (shouldSkipToken(childType) && child.getTextLength() > 0) {
                if (childType == KiteTokenTypes.LBRACE && !inSchemaBody) {
                    inSchemaBody = true;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    inSchemaBody = false;
                }

                // Check for type tokens: IDENTIFIER or built-in type keywords (like ANY)
                boolean isTypeToken = childType == KiteTokenTypes.IDENTIFIER || isBuiltInTypeKeyword(childType);

                if (inSchemaBody && isTypeToken) {
                    if (currentTypeIndex == -1) {
                        // First type token in a pair - this is the type
                        currentTypeIndex = typeTokenIndex;
                        currentTypeLength = child.getTextLength();
                    } else {
                        // Second token in a pair - this is the property name
                        typeIndices.add(currentTypeIndex);
                        typeLengths.add(currentTypeLength);
                        maxTypeLength = Math.max(maxTypeLength, currentTypeLength);

                        propertyIndices.add(typeTokenIndex);
                        propertyLengths.add(child.getTextLength());
                        maxPropertyLength = Math.max(maxPropertyLength, child.getTextLength());

                        currentTypeIndex = -1;  // Reset for next pair
                        currentTypeLength = 0;
                    }
                    typeTokenIndex++;
                }

                // ARRAY_LITERAL element (the [] brackets) - add to current type length
                if (inSchemaBody && currentTypeIndex != -1 && childType == KiteElementTypes.ARRAY_LITERAL) {
                    currentTypeLength += child.getTextLength();  // Adds 2 for "[]"
                }
            }

            child = child.getTreeNext();
        }

        // Pass 2: Build blocks with appropriate padding
        child = myNode.getFirstChildNode();
        inSchemaBody = false;
        int braceDepth = 0;
        boolean insideParens = false;
        boolean insideBrackets = false;
        typeTokenIndex = 0;  // Reset counter
        int pairIndex = 0;    // Which type/property pair we're on
        int currentTypeLengthForPadding = 0;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (shouldSkipToken(childType) && child.getTextLength() > 0) {
                boolean insideBraces = braceDepth > 0;
                Indent childIndent = getChildIndent(childType, insideBraces, insideParens, insideBrackets, braceDepth);

                Integer padding = null;

                if (childType == KiteTokenTypes.LBRACE && !inSchemaBody) {
                    inSchemaBody = true;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    inSchemaBody = false;
                }

                // Check for type tokens: IDENTIFIER or built-in type keywords (like ANY)
                boolean isTypeToken = childType == KiteTokenTypes.IDENTIFIER || isBuiltInTypeKeyword(childType);

                if (inSchemaBody && isTypeToken) {
                    // Check if this type token index matches a type or property
                    if (pairIndex < typeIndices.size() && typeIndices.get(pairIndex) == typeTokenIndex) {
                        // This is a type token - remember its length for the property padding
                        currentTypeLengthForPadding = typeLengths.get(pairIndex);
                    } else if (pairIndex < propertyIndices.size() && propertyIndices.get(pairIndex) == typeTokenIndex) {
                        // This is a property identifier - add padding before it to align property names
                        padding = maxTypeLength - currentTypeLengthForPadding + 1;
                        pairIndex++;  // Move to next pair after processing property
                    }
                    typeTokenIndex++;
                }

                // Calculate padding for '=' to align equals signs
                if (inSchemaBody && childType == KiteTokenTypes.ASSIGN && pairIndex > 0) {
                    // Use the property length from the previous pair
                    int propIdx = pairIndex - 1;
                    if (propIdx < propertyLengths.size()) {
                        padding = maxPropertyLength - propertyLengths.get(propIdx) + 1;
                    }
                }

                blocks.add(new KiteBlock(
                        child,
                        null,
                        null,
                        childIndent,
                        spacingBuilder,
                        padding
                ));

                // Update brace depth AFTER processing current element
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                }

                // Track brackets separately from braces
                if (childType == KiteTokenTypes.LBRACK) {
                    insideBrackets = true;
                } else if (childType == KiteTokenTypes.RBRACK) {
                    insideBrackets = false;
                }

                // Update insideParens state AFTER processing current element
                if (childType == KiteTokenTypes.LPAREN) {
                    insideParens = true;
                } else if (childType == KiteTokenTypes.RPAREN) {
                    insideParens = false;
                }
            }

            child = child.getTreeNext();
        }

        return blocks;
    }

    /**
     * Recursively flattens all tokens in a tree into a list.
     */
    private void flattenTokens(ASTNode node, List<ASTNode> tokens) {
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getFirstChildNode() == null) {
                // Leaf node - add to list
                tokens.add(child);
            } else {
                // Non-leaf node - recurse
                flattenTokens(child, tokens);
            }
            child = child.getTreeNext();
        }
    }

    /**
     * Builds children for FILE node, handling both decorator colons and declaration equals.
     */
    private List<Block> buildFileChildren() {
        List<Block> blocks = new ArrayList<>();

        // Identify alignment groups for both COLON and ASSIGN
        List<AlignmentGroup> colonGroups = containsParenthesizedNamedArgs() ?
                identifyAlignmentGroups(KiteTokenTypes.COLON) : new ArrayList<>();
        List<AlignmentGroup> assignGroups = identifyAlignmentGroups(KiteTokenTypes.ASSIGN);

        ASTNode child = myNode.getFirstChildNode();
        ASTNode previousIdentifier = null;
        boolean insideParens = false;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (shouldSkipToken(childType) && child.getTextLength() > 0) {
                // For declaration elements (var/input/output), inline their children
                if (childType == KiteElementTypes.VARIABLE_DECLARATION ||
                    childType == KiteElementTypes.INPUT_DECLARATION ||
                    childType == KiteElementTypes.OUTPUT_DECLARATION) {
                    // Build blocks for the declaration's children with proper width calculation
                    // FILE-level declarations are never inside braces or brackets
                    buildDeclarationBlocks(child, KiteTokenTypes.ASSIGN, assignGroups, blocks, false, insideParens, false, 0);
                } else {
                    // FILE-level elements are never inside braces or brackets
                    Indent childIndent = getChildIndent(childType, false, insideParens, false, 0);

                    // Track identifiers that precede align tokens
                    if (childType == KiteTokenTypes.IDENTIFIER) {
                        ASTNode nextColon = findNextToken(child, KiteTokenTypes.COLON);
                        ASTNode nextAssign = findNextToken(child, KiteTokenTypes.ASSIGN);
                        if (nextColon != null || nextAssign != null) {
                            previousIdentifier = child;
                        }
                    }

                    // Calculate padding for COLON or ASSIGN tokens
                    Integer padding = null;
                    if (previousIdentifier != null) {
                        if (childType == KiteTokenTypes.COLON) {
                            AlignmentGroup group = findGroupForIdentifier(colonGroups, previousIdentifier);
                            if (group != null) {
                                int keyLength = previousIdentifier.getTextLength();
                                padding = group.maxKeyLength - keyLength; // No extra space for colons
                            }
                            previousIdentifier = null;
                        } else if (childType == KiteTokenTypes.ASSIGN) {
                            AlignmentGroup group = findGroupForIdentifier(assignGroups, previousIdentifier);
                            if (group != null) {
                                int keyLength = previousIdentifier.getTextLength();
                                padding = group.maxKeyLength - keyLength + 1; // +1 for equals
                            }
                            previousIdentifier = null;
                        }
                    }

                    blocks.add(new KiteBlock(
                            child,
                            null,
                            null,
                            childIndent,
                            spacingBuilder,
                            padding
                    ));
                }

                // Update insideParens state AFTER processing current element
                if (childType == KiteTokenTypes.LPAREN) {
                    insideParens = true;
                } else if (childType == KiteTokenTypes.RPAREN) {
                    insideParens = false;
                }
            }

            child = child.getTreeNext();
        }

        return blocks;
    }

    /**
     * Builds blocks for children of a declaration element (INPUT/OUTPUT/VAR).
     * Inlines the declaration's tokens rather than creating a block for the whole declaration.
     */
    private void buildDeclarationBlocks(ASTNode declarationElement,
                                        IElementType alignToken,
                                        List<AlignmentGroup> groups,
                                        List<Block> blocks,
                                        boolean insideBraces,
                                        boolean insideParens,
                                        boolean insideBrackets,
                                        int braceDepth) {
        ASTNode child = declarationElement.getFirstChildNode();
        ASTNode previousIdentifier = null;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (shouldSkipToken(childType) && child.getTextLength() > 0) {
                Indent childIndent = getChildIndent(childType, insideBraces, insideParens, insideBrackets, braceDepth);

                // Track identifiers that precede the align token
                if (childType == KiteTokenTypes.IDENTIFIER) {
                    ASTNode nextToken = findNextToken(child, alignToken);
                    if (nextToken != null) {
                        previousIdentifier = child;
                    }
                }

                // Calculate padding for the align token
                Integer padding = null;
                if (childType == alignToken && previousIdentifier != null) {
                    // Find which group this identifier belongs to
                    AlignmentGroup group = findGroupForIdentifier(groups, previousIdentifier);
                    if (group != null) {
                        // Calculate full declaration width for this identifier
                        int declarationWidth = calculateDeclarationWidth(declarationElement, previousIdentifier);
                        // For ASSIGN, always have at least 1 space; for COLON, longest key has 0 spaces
                        int extraSpace = (alignToken == KiteTokenTypes.ASSIGN) ? 1 : 0;
                        padding = group.maxKeyLength - declarationWidth + extraSpace;
                    }
                    previousIdentifier = null;
                }

                blocks.add(new KiteBlock(
                        child,
                        null,
                        null,
                        childIndent,
                        spacingBuilder,
                        padding
                ));
            }

            child = child.getTreeNext();
        }
    }

    /**
     * Inlines children of an object/array literal directly into the parent's block list.
     * This is critical for proper indentation: by NOT creating a separate block for the literal,
     * the children's indent is calculated relative to the parent's (e.g., RESOURCE_DECLARATION's)
     * baseline, not the physical position of '{' in the source.
     * <p>
     * For example, "tag = {" has the '{' at column 9, but we want content at column 6 (parent + 2).
     * If we created a block for OBJECT_LITERAL, IntelliJ would use column 9 as the baseline.
     * By inlining, we use the parent's baseline and brace depth for correct indentation.
     */
    private void inlineLiteralChildren(ASTNode literalNode,
                                       List<Block> blocks,
                                       boolean parentInsideBraces,
                                       boolean parentInsideParens,
                                       boolean parentInsideBrackets,
                                       int parentBraceDepth) {
        ASTNode child = literalNode.getFirstChildNode();
        // Track brace depth starting from parent's depth
        int braceDepth = parentBraceDepth;
        boolean insideBraces = parentInsideBraces;
        boolean insideParens = parentInsideParens;
        boolean insideBrackets = parentInsideBrackets;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (shouldSkipToken(childType) && child.getTextLength() > 0) {
                // Recursively inline nested object/array literals
                if (childType == KiteElementTypes.OBJECT_LITERAL) {
                    if (isMultiLine(child)) {
                        // Multi-line object: apply colon alignment
                        inlineObjectWithAlignment(child, blocks, insideBraces, insideParens, insideBrackets, braceDepth);
                    } else {
                        // Single-line object: no alignment
                        inlineLiteralChildren(child, blocks, insideBraces, insideParens, insideBrackets, braceDepth);
                    }
                } else if (childType == KiteElementTypes.ARRAY_LITERAL) {
                    inlineLiteralChildren(child, blocks, insideBraces, insideParens, insideBrackets, braceDepth);
                } else {
                    // Calculate indent based on current brace depth
                    Indent childIndent = getChildIndent(childType, insideBraces, insideParens, insideBrackets, braceDepth);

                    blocks.add(new KiteBlock(
                            child,
                            null,
                            null,
                            childIndent,
                            spacingBuilder
                    ));
                }

                // Update brace depth AFTER processing current element
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                    insideBraces = braceDepth > 0;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                    insideBraces = braceDepth > 0;
                }

                // Brackets also contribute to depth for indentation (arrays indent their content)
                if (childType == KiteTokenTypes.LBRACK) {
                    braceDepth++;
                    insideBrackets = true;
                } else if (childType == KiteTokenTypes.RBRACK) {
                    braceDepth--;
                    insideBrackets = false;
                }

                // Update insideParens state AFTER processing current element
                if (childType == KiteTokenTypes.LPAREN) {
                    insideParens = true;
                } else if (childType == KiteTokenTypes.RPAREN) {
                    insideParens = false;
                }
            }

            child = child.getTreeNext();
        }
    }

    /**
     * Inlines children of a multi-line object literal with colon alignment.
     * Similar to inlineLiteralChildren but calculates and applies alignment padding for colons.
     */
    private void inlineObjectWithAlignment(ASTNode literalNode,
                                           List<Block> blocks,
                                           boolean parentInsideBraces,
                                           boolean parentInsideParens,
                                           boolean parentInsideBrackets,
                                           int parentBraceDepth) {
        // First pass: find max key length for direct children only (not nested objects)
        int maxKeyLength = 0;
        ASTNode child = literalNode.getFirstChildNode();
        while (child != null) {
            IElementType childType = child.getElementType();
            if (childType == KiteTokenTypes.IDENTIFIER) {
                ASTNode nextToken = findNextToken(child, KiteTokenTypes.COLON);
                if (nextToken != null) {
                    maxKeyLength = Math.max(maxKeyLength, child.getTextLength());
                }
            }
            child = child.getTreeNext();
        }

        // Second pass: build blocks with alignment padding
        child = literalNode.getFirstChildNode();
        int braceDepth = parentBraceDepth;
        boolean insideBraces = parentInsideBraces;
        boolean insideParens = parentInsideParens;
        boolean insideBrackets = parentInsideBrackets;
        ASTNode previousIdentifier = null;

        while (child != null) {
            IElementType childType = child.getElementType();

            if (shouldSkipToken(childType) && child.getTextLength() > 0) {
                // Recursively handle nested object/array literals
                if (childType == KiteElementTypes.OBJECT_LITERAL) {
                    if (isMultiLine(child)) {
                        inlineObjectWithAlignment(child, blocks, insideBraces, insideParens, insideBrackets, braceDepth);
                    } else {
                        inlineLiteralChildren(child, blocks, insideBraces, insideParens, insideBrackets, braceDepth);
                    }
                } else if (childType == KiteElementTypes.ARRAY_LITERAL) {
                    inlineLiteralChildren(child, blocks, insideBraces, insideParens, insideBrackets, braceDepth);
                } else {
                    Indent childIndent = getChildIndent(childType, insideBraces, insideParens, insideBrackets, braceDepth);

                    // Track identifiers before colons
                    if (childType == KiteTokenTypes.IDENTIFIER) {
                        ASTNode nextToken = findNextToken(child, KiteTokenTypes.COLON);
                        if (nextToken != null) {
                            previousIdentifier = child;
                        }
                    }

                    // Calculate padding for colons
                    Integer padding = null;
                    if (childType == KiteTokenTypes.COLON && previousIdentifier != null) {
                        int keyLength = previousIdentifier.getTextLength();
                        padding = maxKeyLength - keyLength; // No extra space for colons
                        previousIdentifier = null;
                    }

                    blocks.add(new KiteBlock(
                            child,
                            null,
                            null,
                            childIndent,
                            spacingBuilder,
                            padding
                    ));
                }

                // Update brace depth AFTER processing current element
                if (childType == KiteTokenTypes.LBRACE) {
                    braceDepth++;
                    insideBraces = braceDepth > 0;
                } else if (childType == KiteTokenTypes.RBRACE) {
                    braceDepth--;
                    insideBraces = braceDepth > 0;
                }

                // Brackets also contribute to depth for indentation
                if (childType == KiteTokenTypes.LBRACK) {
                    braceDepth++;
                    insideBrackets = true;
                } else if (childType == KiteTokenTypes.RBRACK) {
                    braceDepth--;
                    insideBrackets = false;
                }

                // Update insideParens state AFTER processing current element
                if (childType == KiteTokenTypes.LPAREN) {
                    insideParens = true;
                } else if (childType == KiteTokenTypes.RPAREN) {
                    insideParens = false;
                }
            }

            child = child.getTreeNext();
        }
    }

    /**
     * Identifies groups of consecutive similar declarations.
     * For object literals and decorator args: creates one group (align all together).
     * For block declarations: groups consecutive similar declarations, separated by blank lines.
     */
    private List<AlignmentGroup> identifyAlignmentGroups(IElementType alignToken) {
        List<AlignmentGroup> groups = new ArrayList<>();

        // For object literals and decorator args, use simple grouping (all in one group)
        if (myNode.getElementType() == KiteElementTypes.OBJECT_LITERAL ||
            containsParenthesizedNamedArgs()) {
            return identifySimpleGroup(alignToken);
        }

        // For schema declarations, use special grouping to handle properties with and without default values
        if (myNode.getElementType() == KiteElementTypes.SCHEMA_DECLARATION) {
            return identifySchemaPropertyGroups();
        }

        // For block declarations, use smart grouping
        AlignmentGroup currentGroup = null;
        IElementType lastDeclType = null;
        boolean hasBlankLineSinceLastDecl = false;
        boolean lastWasRegularProperty = false;

        ASTNode child = myNode.getFirstChildNode();
        ASTNode prevChild = null;

        while (child != null) {
            IElementType childType = child.getElementType();

            // Detect blank lines (two consecutive newlines)
            if (childType == KiteTokenTypes.NL || childType == KiteTokenTypes.NEWLINE) {
                if (prevChild != null &&
                    (prevChild.getElementType() == KiteTokenTypes.NL ||
                     prevChild.getElementType() == KiteTokenTypes.NEWLINE)) {
                    hasBlankLineSinceLastDecl = true;
                }
            }

            // Comments also separate alignment groups (they indicate logical sections)
            if (childType == KiteTokenTypes.LINE_COMMENT || childType == KiteTokenTypes.BLOCK_COMMENT) {
                hasBlankLineSinceLastDecl = true;
            }

            // Case 1: Declaration elements (INPUT_DECLARATION, etc.)
            if (childType == KiteElementTypes.INPUT_DECLARATION ||
                childType == KiteElementTypes.OUTPUT_DECLARATION ||
                childType == KiteElementTypes.VARIABLE_DECLARATION) {

                // Start new group if type changed or blank line
                if (currentGroup == null ||
                    childType != lastDeclType ||
                    hasBlankLineSinceLastDecl ||
                    lastWasRegularProperty) {
                    currentGroup = new AlignmentGroup();
                    groups.add(currentGroup);
                    lastDeclType = childType;
                }

                hasBlankLineSinceLastDecl = false;
                lastWasRegularProperty = false;

                // Find the identifier before the align token inside this declaration
                ASTNode identifier = findIdentifierInDeclaration(child, alignToken);
                if (identifier != null) {
                    currentGroup.identifiers.add(identifier);
                    // Calculate full declaration width: keyword + type + identifier
                    int declarationWidth = calculateDeclarationWidth(child, identifier);
                    currentGroup.maxKeyLength = Math.max(currentGroup.maxKeyLength, declarationWidth);
                }
            }
            // Case 2: Regular properties (raw IDENTIFIER followed by align token)
            else if (childType == KiteTokenTypes.IDENTIFIER) {
                ASTNode nextToken = findNextToken(child, alignToken);
                if (nextToken != null) {
                    // Start new group if switching from declarations or blank line
                    if (currentGroup == null ||
                        lastDeclType != null ||
                        hasBlankLineSinceLastDecl) {
                        currentGroup = new AlignmentGroup();
                        groups.add(currentGroup);
                        lastDeclType = null;
                    }

                    hasBlankLineSinceLastDecl = false;
                    lastWasRegularProperty = true;

                    currentGroup.identifiers.add(child);
                    currentGroup.maxKeyLength = Math.max(currentGroup.maxKeyLength, child.getTextLength());
                }
            }

            prevChild = child;
            child = child.getTreeNext();
        }

        return groups;
    }

    /**
     * Identifies alignment groups for schema properties.
     * Schema properties have pattern: typeIdentifier identifier [= value]
     * We want to align ALL property names (second identifier), whether or not they have default values.
     * This method traverses all tokens recursively to handle nested structures.
     */
    private List<AlignmentGroup> identifySchemaPropertyGroups() {
        List<AlignmentGroup> groups = new ArrayList<>();
        AlignmentGroup currentGroup = new AlignmentGroup();
        groups.add(currentGroup);

        // Recursively collect all identifiers that should be aligned
        collectSchemaPropertyIdentifiers(myNode, currentGroup);

        return groups;
    }

    /**
     * Collects schema alignment info with both type and property name lengths.
     */
    private SchemaAlignmentInfo collectSchemaAlignmentInfo() {
        SchemaAlignmentInfo info = new SchemaAlignmentInfo();

        // Flatten all tokens first
        List<ASTNode> allTokens = new ArrayList<>();
        flattenTokens(myNode, allTokens);

        ASTNode typeIdentifier = null;
        boolean inSchemaBody = false;

        for (int i = 0; i < allTokens.size(); i++) {
            ASTNode token = allTokens.get(i);
            IElementType tokenType = token.getElementType();

            // Skip whitespace
            if (tokenType == TokenType.WHITE_SPACE) {
                continue;
            }

            // Track when we're inside the schema body
            if (tokenType == KiteTokenTypes.LBRACE) {
                inSchemaBody = true;
            } else if (tokenType == KiteTokenTypes.RBRACE) {
                break;
            } else if (inSchemaBody && tokenType == KiteTokenTypes.IDENTIFIER) {
                if (typeIdentifier != null) {
                    // This is the property name (second identifier)
                    SchemaPropertyInfo prop = new SchemaPropertyInfo();
                    prop.typeIdentifier = typeIdentifier;
                    prop.propertyIdentifier = token;
                    prop.hasDefaultValue = hasAssignAfter(allTokens, token);

                    info.properties.add(prop);
                    info.maxTypeLength = Math.max(info.maxTypeLength, typeIdentifier.getTextLength());
                    info.maxPropertyLength = Math.max(info.maxPropertyLength, token.getTextLength());

                    typeIdentifier = null; // Reset for next pair
                } else {
                    // This is the type identifier
                    typeIdentifier = token;
                }
            }
            // Reset typeIdentifier at end of property
            else if (tokenType == KiteTokenTypes.ASSIGN ||
                     tokenType == KiteTokenTypes.NL ||
                     tokenType == KiteTokenTypes.NEWLINE ||
                     tokenType == KiteTokenTypes.SEMICOLON) {
                typeIdentifier = null;
            }
        }

        return info;
    }

    /**
     * Recursively collects schema property identifiers from a node and its descendants.
     * Uses flattening to ensure correct pairing across nested structures.
     * Tracks both type and property name to calculate correct alignment.
     */
    private void collectSchemaPropertyIdentifiers(ASTNode node, AlignmentGroup group) {
        // Flatten all tokens first
        List<ASTNode> allTokens = new ArrayList<>();
        flattenTokens(node, allTokens);

        ASTNode typeIdentifier = null;
        boolean inSchemaBody = false;

        for (ASTNode token : allTokens) {
            IElementType tokenType = token.getElementType();

            // Skip whitespace
            if (tokenType == TokenType.WHITE_SPACE) {
                continue;
            }

            // Track when we're inside the schema body
            if (tokenType == KiteTokenTypes.LBRACE) {
                inSchemaBody = true;
            } else if (tokenType == KiteTokenTypes.RBRACE) {
                break;
            } else if (inSchemaBody && tokenType == KiteTokenTypes.IDENTIFIER) {
                if (typeIdentifier != null) {
                    // This is the second identifier in a pair - it's the property name
                    // Add it to the group along with the type length
                    group.identifiers.add(token);

                    // Check if this identifier is followed by ASSIGN
                    boolean hasAssign = hasAssignAfter(allTokens, token);

                    // Calculate combined length: type + space + property name
                    int combinedLength = typeIdentifier.getTextLength() + 1 + token.getTextLength();

                    // For properties without ASSIGN, add 1 to ensure proper alignment
                    int effectiveLength = combinedLength + (hasAssign ? 0 : 1);
                    group.maxKeyLength = Math.max(group.maxKeyLength, effectiveLength);

                    typeIdentifier = null; // Reset for next pair
                } else {
                    // This is the first identifier - the type
                    typeIdentifier = token;
                }
            }
            // Reset typeIdentifier at end of property
            else if (tokenType == KiteTokenTypes.ASSIGN ||
                     tokenType == KiteTokenTypes.NL ||
                     tokenType == KiteTokenTypes.NEWLINE ||
                     tokenType == KiteTokenTypes.SEMICOLON) {
                typeIdentifier = null;
            }
        }
    }

    /**
     * Checks if an identifier is followed by an ASSIGN token (skipping whitespace/newlines).
     */
    private boolean hasAssignAfter(List<ASTNode> tokens, ASTNode identifier) {
        int index = tokens.indexOf(identifier);
        if (index == -1 || index >= tokens.size() - 1) {
            return false;
        }

        // Look ahead for ASSIGN, skipping whitespace and newlines
        for (int i = index + 1; i < tokens.size(); i++) {
            IElementType type = tokens.get(i).getElementType();
            if (type == KiteTokenTypes.ASSIGN) {
                return true;
            }
            if (type != TokenType.WHITE_SPACE &&
                type != KiteTokenTypes.NL &&
                type != KiteTokenTypes.NEWLINE) {
                return false; // Hit a non-whitespace, non-ASSIGN token
            }
        }
        return false;
    }

    /**
     * Creates alignment groups for identifiers, respecting parentheses boundaries.
     * For object literals: one group for all properties.
     * For decorator args (FILE level): separate group for each () pair.
     */
    private List<AlignmentGroup> identifySimpleGroup(IElementType alignToken) {
        List<AlignmentGroup> groups = new ArrayList<>();
        AlignmentGroup group = null;
        int parenDepth = 0;
        boolean inParens = false;

        ASTNode child = myNode.getFirstChildNode();
        while (child != null) {
            IElementType childType = child.getElementType();

            // Track parentheses depth
            if (childType == KiteTokenTypes.LPAREN) {
                parenDepth++;
                if (parenDepth == 1) {
                    // Start new group for this parentheses scope
                    group = new AlignmentGroup();
                    groups.add(group);
                    inParens = true;
                }
            } else if (childType == KiteTokenTypes.RPAREN) {
                parenDepth--;
                if (parenDepth == 0) {
                    inParens = false;
                }
            }
            // Add identifiers to current group
            else if (childType == KiteTokenTypes.IDENTIFIER) {
                ASTNode nextToken = findNextToken(child, alignToken);
                if (nextToken != null) {
                    // For object literals (no parens tracking), create group if needed
                    if (!inParens && group == null) {
                        group = new AlignmentGroup();
                        groups.add(group);
                    }
                    // Only add if we have a group (either in parens or object literal)
                    if (group != null) {
                        group.identifiers.add(child);
                        group.maxKeyLength = Math.max(group.maxKeyLength, child.getTextLength());
                    }
                }
            }

            child = child.getTreeNext();
        }

        return groups;
    }

    /**
     * Calculates the full width of a declaration from keyword to property identifier.
     * For INPUT/OUTPUT/VAR: keyword + type + property identifier (with spaces)
     */
    private int calculateDeclarationWidth(ASTNode declarationElement, ASTNode propertyIdentifier) {
        int width = 0;
        ASTNode child = declarationElement.getFirstChildNode();

        while (child != null && child != propertyIdentifier) {
            IElementType type = child.getElementType();

            // Count keyword tokens
            if (type == KiteTokenTypes.INPUT || type == KiteTokenTypes.OUTPUT ||
                type == KiteTokenTypes.VAR) {
                width += child.getTextLength() + 1; // keyword + space
            }
            // Count type identifier (for input/output/var type name = value)
            else if (type == KiteTokenTypes.IDENTIFIER) {
                width += child.getTextLength() + 1; // type + space
            }

            child = child.getTreeNext();
        }

        // Add the property identifier itself
        if (propertyIdentifier != null) {
            width += propertyIdentifier.getTextLength();
        }

        return width;
    }

    /**
     * Finds the identifier before the align token inside a declaration element.
     * For INPUT/OUTPUT: skips the type, returns the property name
     * For VAR: returns the variable name
     */
    private ASTNode findIdentifierInDeclaration(ASTNode declarationElement, IElementType alignToken) {
        ASTNode child = declarationElement.getFirstChildNode();
        ASTNode lastIdentifier = null;

        while (child != null) {
            IElementType type = child.getElementType();

            // Stop at the align token
            if (type == alignToken) {
                return lastIdentifier;
            }

            // Track identifiers
            if (type == KiteTokenTypes.IDENTIFIER) {
                lastIdentifier = child;
            }

            child = child.getTreeNext();
        }

        return lastIdentifier;
    }

    /**
     * Finds which alignment group an identifier belongs to.
     */
    private AlignmentGroup findGroupForIdentifier(List<AlignmentGroup> groups, ASTNode identifier) {
        for (AlignmentGroup group : groups) {
            if (group.identifiers.contains(identifier)) {
                return group;
            }
        }
        return null;
    }

    /**
     * Finds the next occurrence of a specific token after a node (skipping whitespace).
     *
     * @param node      The starting node
     * @param tokenType The token type to find (COLON or ASSIGN)
     */
    private ASTNode findNextToken(ASTNode node, IElementType tokenType) {
        ASTNode next = node.getTreeNext();
        while (next != null) {
            IElementType type = next.getElementType();
            if (type == tokenType) {
                return next;
            }
            if (type != TokenType.WHITE_SPACE &&
                type != KiteTokenTypes.NL &&
                type != KiteTokenTypes.NEWLINE) {
                return null;
            }
            next = next.getTreeNext();
        }
        return null;
    }

    /**
     * Determines the indent for a child element.
     * Only content between { and } or ( and ) is indented.
     *
     * @param childType      The type of the child element
     * @param insideBraces   Whether we're currently inside braces
     * @param insideParens   Whether we're currently inside parentheses
     * @param insideBrackets Whether we're currently inside array brackets (reserved for future use)
     * @param braceDepth     Current depth of nested braces (for detecting nested objects)
     * @return The appropriate indent for this child
     */
    @SuppressWarnings("unused") // insideBrackets reserved for future array-specific indent rules
    private Indent getChildIndent(IElementType childType, boolean insideBraces, boolean insideParens, boolean insideBrackets, int braceDepth) {
        IElementType parentType = myNode.getElementType();

        // Opening brackets: block's own bracket has no indent, nested brackets get depth-based indent
        if (childType == KiteTokenTypes.LBRACK) {
            if (braceDepth > 0 && isBlockElement(parentType)) {
                int indentSize = 2;
                int spaces = braceDepth * indentSize;
                return Indent.getSpaceIndent(spaces);
            }
            return Indent.getNoneIndent();
        }

        // Closing bracket gets calculated indent based on depth (same as closing braces)
        if (childType == KiteTokenTypes.RBRACK) {
            if (insideBraces && isBlockElement(parentType)) {
                int indentSize = 2;
                int spaces = (braceDepth - 1) * indentSize;
                return Indent.getSpaceIndent(spaces);
            }
            return Indent.getNoneIndent();
        }

        // Special case: OBJECT_LITERAL parents - content gets +2 indent (normal, not continuation)
        // Handle this FIRST so nested literals get proper indentation
        if (parentType == KiteElementTypes.OBJECT_LITERAL) {
            // Opening brace adds 0 indent
            if (childType == KiteTokenTypes.LBRACE) {
                return Indent.getNoneIndent();
            }
            // Closing brace aligns with opening brace (0 indent)
            if (childType == KiteTokenTypes.RBRACE) {
                return Indent.getNoneIndent();
            }
            // Nested object/array literals get normal indent for proper nesting levels
            if (isObjectOrArray(childType)) {
                return Indent.getNormalIndent();
            }
            // Other content gets +2 spaces (normal indent, not +4 continuation)
            return Indent.getNormalIndent();
        }

        // Special case: ARRAY_LITERAL parents - content gets normal indent for consistent alignment
        // Note: LBRACK/RBRACK cases are already handled at the top of this method
        if (parentType == KiteElementTypes.ARRAY_LITERAL) {
            // Nested object/array literals get normal indent for proper nesting
            if (isObjectOrArray(childType)) {
                return Indent.getNormalIndent();
            }
            // All content (elements, object literals, etc.) gets normal indent
            return Indent.getNormalIndent();
        }

        // Object/array literal ELEMENTS (as children of non-literal parents)
        // IMPORTANT: Literals should NOT get NORMAL indent because that causes double indentation.
        // The literal's position is determined by spacing (e.g., "tag = {" - the { follows on the same line).
        // The literal's CONTENT should be indented relative to the parent's baseline, not the literal's position.
        // Using NONE indent here means the literal's baseline = parent's baseline, so children get +2 from there.
        if (isObjectOrArray(childType)) {
            return Indent.getNoneIndent();
        }

        // Closing braces - align based on what they're closing
        // Use calculated spacing for proper nesting alignment
        if (childType == KiteTokenTypes.RBRACE) {
            if (insideBraces && isBlockElement(parentType)) {
                int indentSize = 2; // INDENT_SIZE from code style settings
                // braceDepth=1: Block's own closing brace, aligns with block baseline (0 spaces)
                // braceDepth=2: Object literal's closing brace, aligns with brace opener (2 spaces)
                // braceDepth=3: Nested object's closing brace (4 spaces)
                int spaces = (braceDepth - 1) * indentSize;
                return Indent.getSpaceIndent(spaces);
            }
            return Indent.getNoneIndent();
        }

        // Opening braces: block's own brace has no indent, nested braces get depth-based indent
        if (childType == KiteTokenTypes.LBRACE) {
            if (braceDepth > 0 && isBlockElement(parentType)) {
                int indentSize = 2;
                int spaces = braceDepth * indentSize;
                return Indent.getSpaceIndent(spaces);
            }
            return Indent.getNoneIndent();
        }

        // Content inside braces should be indented based on depth
        if (isBlockElement(parentType)) {
            // For block elements, indent content based on brace depth
            if (insideBraces) {
                // Calculate the proper indent based on brace depth
                // braceDepth=1: resource/component body → +2 (INDENT_SIZE)
                // braceDepth=2: object literal content → +4 (2 * INDENT_SIZE)
                // braceDepth=3: nested object literal → +6 (3 * INDENT_SIZE)
                // etc.
                int indentSize = 2; // INDENT_SIZE from code style settings

                // Content gets indent based on current depth
                int spaces = braceDepth * indentSize;
                return Indent.getSpaceIndent(spaces);
            }
            return Indent.getNoneIndent();
        }

        // Parentheses themselves get no indent
        if (childType == KiteTokenTypes.LPAREN || childType == KiteTokenTypes.RPAREN) {
            return Indent.getNoneIndent();
        }

        // Content inside parentheses should be indented
        if (insideParens) {
            return Indent.getNormalIndent();
        }

        return Indent.getNoneIndent();
    }

    private static boolean isObjectOrArray(IElementType childType) {
        return childType == KiteElementTypes.OBJECT_LITERAL || childType == KiteElementTypes.ARRAY_LITERAL;
    }

    /**
     * Checks if the element type is a block structure that should indent its children.
     */
    private boolean isBlockElement(IElementType type) {
        return type == KiteElementTypes.RESOURCE_DECLARATION ||
               type == KiteElementTypes.COMPONENT_DECLARATION ||
               type == KiteElementTypes.SCHEMA_DECLARATION ||
               type == KiteElementTypes.FUNCTION_DECLARATION ||
               type == KiteElementTypes.FOR_STATEMENT ||
               type == KiteElementTypes.WHILE_STATEMENT;
    }

    @Override
    public @Nullable Spacing getSpacing(@Nullable Block child1, @NotNull Block child2) {
        IElementType parentType = myNode.getElementType();

        // Force line breaks between declaration keywords inside block elements
        // This is critical because we skip NL tokens when building blocks, so we need to
        // explicitly add line breaks between statements/declarations
        if (isBlockElement(parentType) && child1 instanceof KiteBlock && child2 instanceof KiteBlock) {
            IElementType type2 = ((KiteBlock) child2).myNode.getElementType();
            IElementType type1 = ((KiteBlock) child1).myNode.getElementType();

            // If child2 starts a new declaration (INPUT, OUTPUT, VAR, etc.), force a line break
            if (isDeclarationKeyword(type2)) {
                // minSpaces=0, maxSpaces=0, minLineFeeds=1, keepLineBreaks=true, keepBlankLines=1
                return Spacing.createSpacing(0, 0, 1, true, 1);
            }

            // After closing brace, if followed by anything other than closing brace, force line break
            if (type1 == KiteTokenTypes.RBRACE && type2 != KiteTokenTypes.RBRACE) {
                return Spacing.createSpacing(0, 0, 1, true, 1);
            }
        }

        // Special handling for multi-line object and array literals to ensure proper indentation
        // Only force line breaks for multi-line literals; single-line literals stay inline
        if (isObjectOrArray(parentType)) {
            if (isMultiLine(myNode) && child1 instanceof KiteBlock && child2 instanceof KiteBlock) {
                IElementType type1 = ((KiteBlock) child1).myNode.getElementType();
                IElementType type2 = ((KiteBlock) child2).myNode.getElementType();

                // After opening brace/bracket: require line break to trigger indentation
                if (type1 == KiteTokenTypes.LBRACE || type1 == KiteTokenTypes.LBRACK) {
                    return Spacing.createSpacing(1, 1, 1, false, 0);
                }

                // Before closing brace/bracket: require line break to trigger indentation
                if (type2 == KiteTokenTypes.RBRACE || type2 == KiteTokenTypes.RBRACK) {
                    return Spacing.createSpacing(1, 1, 1, false, 0);
                }
            }
        }

        // Use custom padding for aligned tokens (colons, equals, or identifiers for schema properties)
        if (child2 instanceof KiteBlock kiteBlock2) {
            IElementType tokenType = kiteBlock2.myNode.getElementType();

            if (kiteBlock2.alignmentPadding != null &&
                (tokenType == KiteTokenTypes.COLON ||
                 tokenType == KiteTokenTypes.ASSIGN ||
                 tokenType == KiteTokenTypes.IDENTIFIER)) {
                return Spacing.createSpacing(kiteBlock2.alignmentPadding, kiteBlock2.alignmentPadding, 0, true, 0);
            }
        }

        return spacingBuilder.getSpacing(this, child1, child2);
    }

    /**
     * Checks if the token type is a declaration keyword that starts a new statement.
     */
    private boolean isDeclarationKeyword(IElementType type) {
        return type == KiteTokenTypes.INPUT ||
               type == KiteTokenTypes.OUTPUT ||
               type == KiteTokenTypes.VAR ||
               type == KiteTokenTypes.RESOURCE ||
               type == KiteTokenTypes.COMPONENT ||
               type == KiteTokenTypes.SCHEMA ||
               type == KiteTokenTypes.FUN ||
               type == KiteTokenTypes.TYPE ||
               type == KiteTokenTypes.IF ||
               type == KiteTokenTypes.FOR ||
               type == KiteTokenTypes.WHILE ||
               type == KiteTokenTypes.RETURN;
    }

    /**
     * Checks if the token type is a built-in type keyword (like 'any').
     * These keywords can appear as type identifiers in schema property declarations.
     */
    private boolean isBuiltInTypeKeyword(IElementType type) {
        return type == KiteTokenTypes.ANY;
    }

    @Override
    public boolean isLeaf() {
        return myNode.getFirstChildNode() == null;
    }

    @Override
    public @NotNull Indent getIndent() {
        return indent;
    }

    @Override
    public @NotNull ChildAttributes getChildAttributes(int newChildIndex) {
        IElementType type = myNode.getElementType();

        // Inside block structures, use normal indentation
        if (isBlockElement(type) ||
            type == KiteElementTypes.OBJECT_LITERAL ||
            type == KiteElementTypes.ARRAY_LITERAL) {
            return new ChildAttributes(Indent.getNormalIndent(), null);
        }

        return new ChildAttributes(Indent.getNoneIndent(), null);
    }

    /**
     * Represents a group of consecutive declarations that should align together.
     */
    private static class AlignmentGroup {
        int maxKeyLength;
        final List<ASTNode> identifiers = new ArrayList<>();
    }

    /**
     * For schema properties, we need to track both type and property name for two-stage alignment.
     */
    private static class SchemaPropertyInfo {
        ASTNode typeIdentifier;
        ASTNode propertyIdentifier;
        boolean hasDefaultValue;
    }

    private static class SchemaAlignmentInfo {
        int maxTypeLength = 0;
        int maxPropertyLength = 0;
        final List<SchemaPropertyInfo> properties = new ArrayList<>();
    }
}
